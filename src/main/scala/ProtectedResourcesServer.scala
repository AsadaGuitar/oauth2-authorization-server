import akka.http.scaladsl.model.{ContentTypes, HttpEntity}
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.server.directives.Credentials.Provided
import repository.{Account, AccountRepository, TokenRepository}

import scala.concurrent.duration.Duration
import scala.concurrent.{Await, ExecutionContextExecutor, Future}

trait ProtectedResourcesServer extends TokenRepository with AccountRepository {

  implicit val ec: ExecutionContextExecutor

  def accountJson(account: Account): String =
    s"""{
       | 'id':'${account.id}',
       | 'name':'${account.name}',
       | 'password':'${account.password}',
       | 'email':'${account.email}',
       | 'created':'${account.created}'
       |}
       |""".stripMargin

  val protectedResourcesServer: Route =
    pathPrefix("account" / Remaining) { id =>
      post {
        authenticateOAuth2Async("bearer", {
          case p@Provided(token) =>
            this.findToken(id).map(_.filter(_.accessToken == token))

          case _ => Future(None)
        }) { bearerToken =>
          val account: Future[Option[Account]] = this.findAccount(bearerToken.clientId)
          val r = Await.result(account, Duration.Inf)
          r match {
            case Some(value) => complete(HttpEntity(ContentTypes.`application/json`, accountJson(value)))
            case None =>
              complete(401, HttpEntity(ContentTypes.`text/plain(UTF-8)`, "Not Found repository.Account."))
          }
        }
      }
    }
}
