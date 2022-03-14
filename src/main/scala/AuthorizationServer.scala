import akka.http.scaladsl.model.StatusCodes.{PermanentRedirect, Redirection}
import akka.http.scaladsl.model.Uri.Query
import akka.http.scaladsl.model.{ContentTypes, HttpEntity, StatusCodes, Uri}
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.server.directives.Credentials.Provided
import com.softwaremill.session.SessionDirectives.setSession
import com.softwaremill.session.{SessionDirectives, SessionManager}
import com.softwaremill.session.SessionOptions.{oneOff, usingCookies}
import org.fusesource.scalate.{TemplateEngine, TemplateSource}
import repository.{AccountRepository, AuthorizationCodeParameters, AuthorizationCodeParametersRepository, ClientRepository, RedirectUrlRepository, TokenRepository}

import java.io.File
import scala.concurrent.duration.Duration
import scala.concurrent.{Await, ExecutionContextExecutor, Future}
import scala.util.Try
import com.softwaremill.session._
import data.AuthorizationEndPointQueryParameters


case class AuthorizationForm(id: String, code: String)


object AuthorizationServer {

  private val verifyFormFile: File = new File("./src/main/resources/templates/approve.mustache")

  private val engine = new TemplateEngine

  def verifyForm(requestId: String): String = {
    engine.layout(TemplateSource.fromFile(verifyFormFile), Map("requestId" -> requestId))
  }

  def generateRequestId(): String = scala.util.Random.alphanumeric.take(8).mkString
  def generateAuthorizationCode(): String = scala.util.Random.alphanumeric.take(128).mkString


  // Session
  implicit def AuthorizationEndPointQueryParametersSerializer: SessionSerializer[AuthorizationEndPointQueryParameters, String] = {
    new MultiValueSessionSerializer[AuthorizationEndPointQueryParameters](
      toMap = {
        case AuthorizationEndPointQueryParameters(requestId, responseType, state, redirectUri, scope) =>
          Map("requestId" -> requestId, "responseType" -> responseType, "state" -> state, "redirectUri" -> redirectUri, "scope" -> scope)
      },
      fromMap = m => Try {
        AuthorizationEndPointQueryParameters(
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
  implicit val sessionManager: SessionManager[AuthorizationEndPointQueryParameters] =
    new SessionManager[AuthorizationEndPointQueryParameters](sessionConfig)
}


trait AuthorizationServer extends TokenRepository
  with AccountRepository with ClientRepository
  with RedirectUrlRepository with OAuthMarshaller with AuthorizationCodeParametersRepository {

  import AuthorizationServer._
  import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._

  implicit val ec: ExecutionContextExecutor

  private val unsupportedResponseType = complete(401, HttpEntity(ContentTypes.`text/plain(UTF-8)`, "Unsupported response_type."))

  val authorizationServer: Route =
    pathPrefix("authorization") {
      pathEndOrSingleSlash {
        get {
          parameters("response_type", "client_id",
            "state", "redirect_uri", "scope") { (responseType,clientId,state,redirectUri,scope) =>
              if (responseType.toLowerCase.equals("code")) {
                val client = this.findAllRedirectUrl(clientId)
                val r = Await.result(client, Duration.Inf)
                r match {
                  case list if list.exists(url => url.redirectUrl == redirectUri) =>
                    val requestId = generateRequestId()
                    val params = AuthorizationEndPointQueryParameters(requestId, responseType, state, redirectUri, scope)
                    setSession(oneOff, usingCookies, params) {
                      val template = verifyForm(requestId)
                      complete(HttpEntity(ContentTypes.`text/html(UTF-8)`, template))
                    }
                  case _ => complete(401, HttpEntity(ContentTypes.`text/plain(UTF-8)`, "Invalid Redirect Url."))
                }
              } else {
                unsupportedResponseType
              }
          }
        }
      }
    } ~ pathPrefix("approve") {
      pathEndOrSingleSlash {
        post {
          formFields("request-id", "approve") { (requestId, approve) =>
            SessionDirectives.requiredSession(oneOff, usingCookies) {
              case params@AuthorizationEndPointQueryParameters(sessionRequestId, responseType, state, redirectUri, scope) =>
                responseType match {
                  case "code" =>
                    if (sessionRequestId.equals(requestId)) {
                      approve match {
                        case "approve" =>
                          val code = generateAuthorizationCode()
                          this.createAuthorizationCodeParameters(AuthorizationCodeParameters(code, requestId, responseType, state, redirectUri, scope))
                          val redirectUriWithParams = Uri(redirectUri).withQuery(Query("code" -> code, "state" -> state))
                          redirect(redirectUriWithParams, StatusCodes.PermanentRedirect)

                        case "deny"    =>
                          val reason = Redirection.apply(401)(reason = "access denied.", "", "", allowsEntity = false)
                          redirect(redirectUri, reason)
                      }
                    } else {
                      complete(401, HttpEntity(ContentTypes.`text/html(UTF-8)`, "<h2>No matching authorization request.</h2>"))
                    }
                  case _ =>
                    val reason = Redirection.apply(401)(reason = "unsupported response type", "", "", allowsEntity = false)
                    redirect(redirectUri, reason)
                }
              case _ =>
                complete(HttpEntity(ContentTypes.`text/plain(UTF-8)`, "Invalid request."))
            }
          }
        }
      }
    } ~
      pathPrefix("token") {
        post {
          authenticateBasicAsync("basic", {
            case p@Provided(id) =>
              this.findAccount(id)
                .map(_.filter(account => p.verify(account.password)))
            case _ => Future(None)
          }) { account =>
            entity(as[AuthorizationForm]) { form =>
              complete(HttpEntity(ContentTypes.`text/plain(UTF-8)`, ""))
            }
          }
        }
      }

}
