package repository

import slick.ast.ColumnOption.PrimaryKey
import slick.jdbc.JdbcBackend.Database
import slick.lifted.{TableQuery, Tag}
import slick.jdbc.PostgresProfile.api._
import slick.migration.api.{PostgresDialect, SqlMigration, TableMigration}

import scala.concurrent.{ExecutionContext, Future}


object AuthorizationCodeParametersRepository {
  implicit val dialect: PostgresDialect = new PostgresDialect

  private val initClient =
    TableMigration(AuthorizationCodeParametersTable.table)
//      .drop
      .create
      .addColumns(_.code, _.requestId, _.responseType, _.state, _.redirectUri, _.scope)

  private val migration = initClient

  def runMigration(db: Database) = db.run(migration())
}

trait AuthorizationCodeParametersRepository {

  protected val db: Database
  implicit val ec: ExecutionContext

  def createAuthorizationCodeParameters(authorizationCodeParameters: AuthorizationCodeParameters): Future[Int] =
    db.run(AuthorizationCodeParametersTable.table += authorizationCodeParameters)

  def findAuthorizationCodeParameters(code: String): Future[Option[AuthorizationCodeParameters]] = {
    val query = for (p <- AuthorizationCodeParametersTable.table if p.code === code) yield p
    db.run(query.result).map{
      case Nil => None
      case x   => Some(x.head)
    }
  }
}

case class AuthorizationCodeParameters(code: String, requestId: String, responseType: String,
                                       state: String, redirectUri: String, scope: String)

object  AuthorizationCodeParametersTable {
  val table = TableQuery[AuthorizationCodeParametersTable]
}

class  AuthorizationCodeParametersTable(tag: Tag)
  extends Table[AuthorizationCodeParameters](tag,"authorization_code_parameters"){

  def code = column[String]("code", PrimaryKey)
  def requestId = column[String]("request_id")
  def responseType = column[String]("response_type")
  def state = column[String]("state")
  def redirectUri = column[String]("redirect_uri")
  def scope = column[String]("scope")

  override def * =
    (code, requestId, responseType, state, redirectUri, scope) <> (
      (AuthorizationCodeParameters.apply _).tupled,
      AuthorizationCodeParameters.unapply
    )
}

