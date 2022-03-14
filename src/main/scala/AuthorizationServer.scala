import akka.event.Logging
import akka.http.scaladsl.model.StatusCodes.Redirection
import akka.http.scaladsl.model.Uri.Query
import akka.http.scaladsl.model.{ContentTypes, HttpEntity, StatusCodes, Uri}
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.server.directives.Credentials.Provided
import com.emarsys.jwt.akka.http.{JwtAuthentication, JwtConfig}
import com.softwaremill.session.SessionDirectives.setSession
import com.softwaremill.session.{SessionDirectives, SessionManager}
import com.softwaremill.session.SessionOptions.{oneOff, usingCookies}
import org.fusesource.scalate.{TemplateEngine, TemplateSource}
import repository.{AccountRepository, ClientRepository, RedirectUriRepository, RequestParametersRepository, TokenRepository}

import java.io.File
import scala.concurrent.duration.Duration
import scala.concurrent.{Await, ExecutionContextExecutor, Future}
import scala.util.{Failure, Success, Try}
import com.softwaremill.session._
import data.OAuth2Models.{BearerToken, RequestIdParameters, RequestParameters, TokenEndPointRequest}
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import com.typesafe.config.Config



object AuthorizationServer {

  private val verifyFormFile: File = new File("./src/main/resources/templates/approve.mustache")

  private val engine = new TemplateEngine

  def verifyForm(requestId: String): String = {
    engine.layout(TemplateSource.fromFile(verifyFormFile), Map("requestId" -> requestId))
  }

  def generateRequestId(): String = scala.util.Random.alphanumeric.take(8).mkString
  def generateAuthorizationCode(): String = scala.util.Random.alphanumeric.take(128).mkString


  // Session
  implicit def RequestIdParametersSerializer: SessionSerializer[RequestIdParameters, String] = {
    new MultiValueSessionSerializer[RequestIdParameters](
      toMap = {
        case RequestIdParameters(requestId, responseType, state, redirectUri, scope) =>
          Map("requestId" -> requestId, "responseType" -> responseType, "state" -> state, "redirectUri" -> redirectUri, "scope" -> scope)
      },
      fromMap = m => Try {
        RequestIdParameters(
          requestId    = m("requestId"),
          responseType = m("responseType"),
          state        = m("state"),
          redirectUri  = m("redirectUri"),
          scope        = m("scope")
        )
      }
    )
  }

  val sessionConfig: SessionConfig = SessionConfig.default(SessionUtil.randomServerSecret())
  implicit val sessionManager: SessionManager[RequestIdParameters] =
    new SessionManager[RequestIdParameters](sessionConfig)


}


trait AuthorizationServer extends TokenRepository
  with AccountRepository with ClientRepository
  with RedirectUriRepository with RequestParametersRepository
  with OAuthMarshaller  {

  import AuthorizationServer._

  implicit val ec: ExecutionContextExecutor
  val config: Config

  private val jwtAuthentication: JwtAuthentication = new JwtAuthentication {
    override val jwtConfig: JwtConfig = new JwtConfig(config)
  }

  val authorizationServer: Route =
    pathPrefix("authorization") {
      pathEndOrSingleSlash {
        get {
          parameters("client_id", "response_type", "state", "redirect_uri", "scope") {
            (clientId, responseType, state, redirectUri, scope) =>
              if (responseType.toLowerCase.equals("code")) {
                val redirectUriList = this.findAllRedirectUrl(clientId)
                val r = Await.result(redirectUriList, Duration.Inf)
                r match {
                  case list if list.exists(uriData => uriData.uri == redirectUri) =>
                    val requestId = generateRequestId()
                    val params = RequestIdParameters(requestId, responseType, state, redirectUri, scope)
                    setSession(oneOff, usingCookies, params) {
                      val template = verifyForm(requestId)
                      complete(HttpEntity(ContentTypes.`text/html(UTF-8)`, template))
                    }
                  case _ => complete(401, HttpEntity(ContentTypes.`text/plain(UTF-8)`, "Invalid Redirect Url."))
                }
              } else {
                complete(401, HttpEntity(ContentTypes.`text/plain(UTF-8)`, "Unsupported response_type."))
              }
          }
        }
      }
    } ~ pathPrefix("approve") {
      pathEndOrSingleSlash {
        post {
          formFields("request-id", "username", "password") { (requestId, username, password) =>
            SessionDirectives.requiredSession(oneOff, usingCookies) {
              case RequestIdParameters(sessionRequestId, responseType, state, redirectUri, scope) =>
                responseType match {
                  case "code" =>
                    if (sessionRequestId.equals(requestId)) {
                      onComplete(this.findAccount(username)) {
                        case Success(account) if account.exists(_.password.equals(password)) =>
                          account.fold{
                            complete(400, HttpEntity(ContentTypes.`text/html(UTF-8)`, "<h2>User does not exist.</h2>"))
                          } { _ =>
                            val code = generateAuthorizationCode()
                            this.createRequestParameters(RequestParameters(code, username, requestId, responseType, state, redirectUri, scope, None))
                            val redirectUriWithQuery = Uri(redirectUri).withQuery(Query("code" -> code, "state" -> state))
                            redirect(redirectUriWithQuery, StatusCodes.PermanentRedirect)
                          }
                        case Failure(exception) =>
                          logRequest(exception.getMessage, Logging.ErrorLevel) {
                            complete(500, HttpEntity(ContentTypes.`text/html(UTF-8)`, "<h2>Server error occurred.</h2>"))
                          }
                        case _ => complete(400, HttpEntity(ContentTypes.`text/html(UTF-8)`, "<h2>Invalid password.</h2>"))
                      }
                    } else {
                      complete(401, HttpEntity(ContentTypes.`text/html(UTF-8)`, "<h2>No matching authorization request.</h2>"))
                    }
                  case _ =>
                    val reason = Redirection.apply(401)(reason = "unsupported response type", "", "", allowsEntity = false)
                    redirect(redirectUri, reason)
                }
              case _ =>
                complete(401, HttpEntity(ContentTypes.`text/plain(UTF-8)`, "Invalid request."))
            }
          }
        }
      }
    } ~
      pathPrefix("token") {
        post {
          authenticateBasicAsync("basic", {
            case p@Provided(id) =>
              this.findClient(id)
                .map(_.filter(client => p.verify(client.secret)))
            case _ => Future(None)
          }) { client =>
            entity(as[TokenEndPointRequest]) {
              case TokenEndPointRequest(grantType, code) if grantType.toLowerCase.equals("authorization_code") =>
                onComplete(this.takeRequestParameters(code)) {
                  case Success(requestParameters) => requestParameters match {
                    case Some(params) =>
                      if (params.clientId.equals(client.id)) {
                        val accessToken = jwtAuthentication.generateToken(params.username)
                        onComplete(this.createToken(BearerToken(client.id, accessToken, None))) {
                          case Success(_) =>
                            val json = s"{token_type:'Bearer', access_token:'$accessToken'}"
                            complete(HttpEntity(ContentTypes.`application/json`, json))
                          case Failure(exception) =>
                            logRequest(exception.getMessage, Logging.ErrorLevel) {
                              complete(500, HttpEntity(ContentTypes.`text/html(UTF-8)`, "<h2>Server error occurred.</h2>"))
                            }
                        }
                      } else {
                        complete(400, HttpEntity(ContentTypes.`text/plain(UTF-8)`, "Invalid grant."))
                      }
                    case None => complete(400, HttpEntity(ContentTypes.`text/plain(UTF-8)`, "Invalid grant."))
                  }
                  case Failure(exception) =>
                    logRequest(exception.getMessage, Logging.ErrorLevel) {
                      complete(500, HttpEntity(ContentTypes.`text/html(UTF-8)`, "<h2>Server error occurred.</h2>"))
                    }
                }
              case _ => complete(400, HttpEntity(ContentTypes.`text/plain(UTF-8)`, "unsupported grant type."))
            }
            complete(HttpEntity(ContentTypes.`text/plain(UTF-8)`,"" ))
          }
        }
      }

}
