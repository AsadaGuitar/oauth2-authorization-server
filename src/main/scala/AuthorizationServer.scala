import AuthorizationServer.verifyForm
import akka.http.scaladsl.model.{ContentTypes, HttpEntity}
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.server.directives.Credentials.Provided
import akka.http.scaladsl.server.directives.RouteDirectives
import org.fusesource.scalate.{TemplateEngine, TemplateSource}
import repository.{AccountRepository, ClientRepository, RedirectUrlRepository, TokenRepository}

import java.io.File
import scala.concurrent.duration.Duration
import scala.concurrent.{Await, ExecutionContextExecutor, Future}

case class AuthorizationForm(id: String, code: String)

object AuthorizationServer {
  private val verifyFormFile: File = new File("./src/main/resources/templates/approve.mustache")

  private val engine = new TemplateEngine

  private def someAttributes(requestId: String, scope: Option[String]): Map[String, String] ={
    val attributes = Map("requestId" -> requestId)
    scope match {
      case Some(value) => attributes ++ Map("scope" -> value)
      case None => attributes
    }
  }

  def verifyForm(requestId: String, scope: Option[String]): String = {
    engine.layout(TemplateSource.fromFile(verifyFormFile), someAttributes(requestId, scope))
  }
}

trait AuthorizationServer extends TokenRepository
  with AccountRepository with ClientRepository
  with RedirectUrlRepository with OAuthMarshaller {
  import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._


  implicit val ec: ExecutionContextExecutor

  val authorizationServer: Route =
    pathPrefix("auth") {
      pathPrefix("authorizationEndPoint") {
        get {
          parameters("response_type", "client_id",
            "state", "redirect_url", "scope".optional) {
            (responseType, clientId, state, redirectUrl, scope) =>
              if (responseType.toLowerCase.equals("code")) {
                val client = this.findAllRedirectUrl(clientId)
                val r = Await.result(client, Duration.Inf)
                r match {
                  case list if list.exists(url => url.redirectUrl == redirectUrl) =>
                    val template = verifyForm("", scope)
                    complete(HttpEntity(ContentTypes.`text/html(UTF-8)`, template))
                  case _ => complete(401, HttpEntity(ContentTypes.`text/plain(UTF-8)`, "Invalid Redirect Url."))
                }
              } else {
                RouteDirectives.
                complete(401, HttpEntity(ContentTypes.`text/plain(UTF-8)`, "Unsupported response_type."))
              }
          }
        }
      } ~
      pathPrefix("tokenEndPoint") {
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
}
