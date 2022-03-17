import akka.event.Logging
import akka.http.scaladsl.model.{ContentTypes, HttpEntity}
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.server.directives.Credentials.Provided
import com.emarsys.jwt.akka.http.{JwtAuthentication, JwtConfig}
import com.typesafe.config.Config
import repository.{Account, AccountRepository, TokenRepository}
import spray.json.{RootJsonFormat, enrichAny}

import scala.concurrent.{Await, ExecutionContextExecutor, Future}
import scala.util.{Failure, Success}



trait ProtectedResourcesServer extends TokenRepository
  with AccountRepository with JwtAuthentication with OAuthMarshaller {

  implicit val ec: ExecutionContextExecutor
  val config: Config

  override lazy val jwtConfig: JwtConfig = new JwtConfig(config)

  val protectedResourcesServer: Route =
    pathPrefix("account" / Remaining) { id =>
      get {
        logRequest(s"/account/$id") {
          authenticateOAuth2("Bearer", {
            case Provided(identifier) => Some(identifier)
            case _ => None
          }) { token =>
            jwtAuthenticateToken(Some(token), as[String]) { tokenId =>
              val username = tokenId.filter(c => c != '{' && c != '}')
                if (username.equals(id)) {
                  onComplete(this.findAccount(username)) {
                    case Success(accountOption) =>
                      accountOption.fold {
                        complete(402, HttpEntity(ContentTypes.`application/json`, "Not found account."))
                      } { account =>
                        complete(HttpEntity(ContentTypes.`application/json`, account.toJson.toString()))
                      }
                    case Failure(exception) => logRequest(exception.getMessage, Logging.ErrorLevel) {
                      complete(500, HttpEntity(ContentTypes.`application/json`, "Server error occurred."))
                    }
                  }
                } else {
                  complete(402, HttpEntity(ContentTypes.`application/json`, "Invalid user id."))
                }
            }
          }
        }
      }
    }
}
