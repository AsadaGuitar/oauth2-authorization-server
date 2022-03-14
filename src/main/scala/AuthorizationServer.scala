import AuthorizationServer.{generateRequestId, verifyForm}
import akka.http.scaladsl.model.{ContentTypes, HttpEntity}
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.server.directives.Credentials.Provided
import com.softwaremill.session.SessionDirectives.setSession
import com.softwaremill.session.{SessionDirectives, SessionManager}
import com.softwaremill.session.SessionOptions.{oneOff, usingCookies}
import data.RequestId
import org.fusesource.scalate.{TemplateEngine, TemplateSource}
import repository.{AccountRepository, ClientRepository, RedirectUrlRepository, TokenRepository}

import java.io.File
import scala.concurrent.duration.Duration
import scala.concurrent.{Await, ExecutionContextExecutor, Future}


case class AuthorizationForm(id: String, code: String)


object AuthorizationServer {

  private val verifyFormFile: File = new File("./src/main/resources/templates/approve.mustache")

  private val engine = new TemplateEngine

  def verifyForm(requestId: String): String = {
    engine.layout(TemplateSource.fromFile(verifyFormFile), Map("requestId" -> requestId))
  }

  def generateRequestId(): String = scala.util.Random.alphanumeric.take(8).mkString

}

trait AuthorizationServer extends TokenRepository
  with AccountRepository with ClientRepository
  with RedirectUrlRepository with OAuthMarshaller {
  import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._


  implicit val ec: ExecutionContextExecutor
  implicit val sessionManager: SessionManager[RequestId]

  val authorizationServer: Route =
    pathPrefix("authorization") {
      pathEndOrSingleSlash {
        get {
          parameters("response_type", "client_id",
            "state", "redirect_url", "scope".optional) {
            (responseType, clientId, state, redirectUrl, scope) =>
              if (responseType.toLowerCase.equals("code")) {
                val client = this.findAllRedirectUrl(clientId)
                val r = Await.result(client, Duration.Inf)
                r match {
                  case list if list.exists(url => url.redirectUrl == redirectUrl) =>
                    val requestId = generateRequestId()
                    setSession(oneOff, usingCookies, RequestId(requestId)) {
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
          formFields("request-id", "approve") { (requestId, approve) =>
            SessionDirectives.requiredSession(oneOff, usingCookies) {
              case RequestId(sessionRequestId) if requestId.equals(sessionRequestId) =>
                approve match {
                  case "approve" => complete(401, HttpEntity(ContentTypes.`text/plain(UTF-8)`, "Access denied."))
                  case "deny"    => complete(401, HttpEntity(ContentTypes.`text/plain(UTF-8)`, "Access denied."))
                }
              case _ => complete(401, HttpEntity(ContentTypes.`text/plain(UTF-8)`, "No matching authorization request."))
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
