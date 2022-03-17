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

import scala.concurrent.{ExecutionContextExecutor, Future}
import scala.util.{Failure, Success, Try}
import com.softwaremill.session._
import data.OAuth2Models.{BearerToken, RequestIdParameters, RequestParameters, TokenEndPointRequest}
import com.typesafe.config.Config



object AuthorizationServer {

  private val engine = new TemplateEngine

  def verifyForm(config: Config, requestId: String): String = {
    val approveFile = config.getString("auth.file.approve")
    engine.layout(TemplateSource.fromFile(approveFile), Map("requestId" -> requestId))
  }

  def generateRequestId(): String = scala.util.Random.alphanumeric.take(8).mkString
  def generateAuthorizationCode(): String = scala.util.Random.alphanumeric.take(128).mkString

  // Session
  implicit def RequestIdParametersSerializer: SessionSerializer[RequestIdParameters, String] = {
    new MultiValueSessionSerializer[RequestIdParameters](
      toMap = {
        case RequestIdParameters(requestId, clientId, responseType, state, redirectUri, scope) =>
          Map("requestId" -> requestId, "clientId" -> clientId, "responseType" -> responseType,
            "state" -> state, "redirectUri" -> redirectUri, "scope" -> scope)
      },
      fromMap = m => Try {
        RequestIdParameters(
          requestId    = m("requestId"),
          clientId     = m("clientId"),
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

  private lazy val jwtAuthentication: JwtAuthentication = new JwtAuthentication {
    override val jwtConfig: JwtConfig = new JwtConfig(config)
  }

  val authorizationServer: Route =
    pathPrefix("authorization") {
      pathEndOrSingleSlash {
        get {
          parameters("client_id", "response_type", "state", "redirect_uri", "scope") {
            (clientId, responseType, state, redirectUri, scope) =>
              if (responseType.toLowerCase.equals("code")) {
                onComplete(this.findAllRedirectUrl(clientId)) {
                  case Success(redirectUriList) =>
                    if (redirectUriList.nonEmpty) {
                      val requestId = generateRequestId()
                      val params = RequestIdParameters(requestId, clientId, responseType, state, redirectUri, scope)
                      setSession(oneOff, usingCookies, params) {
                        val template = verifyForm(config, requestId)
                        complete(HttpEntity(ContentTypes.`text/html(UTF-8)`, template))
                      }
                    } else {
                      complete(401, HttpEntity(ContentTypes.`text/html(UTF-8)`, "<h2>Invalid Redirect Url.</h2>"))
                    }
                  case Failure(exception) =>
                    logRequest(exception.getMessage, Logging.ErrorLevel) {
                      complete(500, HttpEntity(ContentTypes.`text/html(UTF-8)`, "<h2>Server error occurred.</h2>"))
                    }
                }
              } else {
                complete(401, HttpEntity(ContentTypes.`text/html(UTF-8)`, "<h2>Unsupported response_type.</h2>"))
              }
          }
        }
      }
    } ~ pathPrefix("approve") {
      pathEndOrSingleSlash {
        post {
          logRequest("[/approve] [POST] ", Logging.DebugLevel) {
            formFields("request-id", "username", "password") { (requestId, username, password) =>
              SessionDirectives.requiredSession(oneOff, usingCookies) {
                case RequestIdParameters(sessionRequestId, clientId, responseType, state, redirectUri, scope) =>
                  responseType match {
                    case "code" =>
                      logRequest("[/approve] [POST] [Param: response_type = code]") {
                        if (sessionRequestId.equals(requestId)) {
                          onComplete(this.findAccount(username)) {
                            case Success(account) if account.exists(_.password.equals(password)) =>
                              account.fold{
                                complete(400, HttpEntity(ContentTypes.`text/html(UTF-8)`, "<h2>User does not exist.</h2>"))
                              } { _ =>
                                val code = generateAuthorizationCode()
                                this.createRequestParameters(RequestParameters(code, username, clientId, responseType, state, redirectUri, scope, None))
                                val redirectUriWithQuery = Uri(redirectUri).withQuery(Query("code" -> code, "state" -> state))
                                redirect(redirectUriWithQuery, StatusCodes.SeeOther)
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
                            val json =
                              s"""{
                                  |\"token_type\":\"Bearer\",
                                  |\"username\":\"${params.username}\",
                                  |\"access_token\":\"$accessToken\"
                                 |}""".stripMargin
                            complete(HttpEntity(ContentTypes.`application/json`, json))
                          case Failure(exception) =>
                            logRequest(exception.getMessage, Logging.ErrorLevel) {
                              complete(500, HttpEntity(ContentTypes.`text/plain(UTF-8)`, "Server error occurred."))
                            }
                        }
                      } else {
                        complete(400, HttpEntity(ContentTypes.`text/plain(UTF-8)`, "Invalid grant."))
                      }
                    case None => complete(400, HttpEntity(ContentTypes.`text/plain(UTF-8)`, "Invalid grant."))
                  }
                  case Failure(exception) =>
                    logRequest(exception.getMessage, Logging.ErrorLevel) {
                      complete(500, HttpEntity(ContentTypes.`text/plain(UTF-8)`, "Server error occurred."))
                    }
                }
              case _ => complete(400, HttpEntity(ContentTypes.`text/plain(UTF-8)`, "unsupported grant type."))
            }
          }
        }
      }
}
