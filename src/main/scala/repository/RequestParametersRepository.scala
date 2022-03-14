package repository

import data.OAuth2Models.RequestParameters
import slick.ast.ColumnOption._
import slick.jdbc.JdbcBackend.Database
import slick.lifted.{TableQuery, Tag}
import slick.jdbc.PostgresProfile.api._
import slick.migration.api.{PostgresDialect, TableMigration}
import slick.sql.SqlProfile.ColumnOption._

import java.sql.Timestamp
import scala.concurrent.{ExecutionContext, Future}


object RequestParametersRepository {
  implicit val dialect: PostgresDialect = new PostgresDialect

  private val initClient =
    TableMigration(RequestParametersTable.table)
      .drop
      .create
      .addColumns(_.code, _.username, _.clientId, _.responseType, _.state, _.redirectUri, _.scope, _.created)

  private val migration = initClient

  def runMigration(db: Database) = db.run(migration())
}

trait RequestParametersRepository {

  protected val db: Database
  implicit val ec: ExecutionContext

  def createRequestParameters(requestParameters: RequestParameters): Future[Int] = db.run(
    requestParameters.created match {
      case Some(_) => RequestParametersTable.table += requestParameters
      case None    =>
        sqlu"""INSERT INTO request_parameters(code, username, client_id,response_type, state, redirect_uri, scope)
                VALUES (${requestParameters.code}, ${requestParameters.username},
                        ${requestParameters.clientId}, ${requestParameters.responseType},
                        ${requestParameters.state}, ${requestParameters.redirectUri},
                        ${requestParameters.scope});"""
    }
   )

  def findRequestParameters(code: String): Future[Option[RequestParameters]] = {
    val query = for (p <- RequestParametersTable.table if p.code === code) yield p
    db.run(query.result).map{
      case Nil => None
      case x   => Some(x.head)
    }
  }

  def deleteRequestParameters(code: String): Future[Int] = db.run {
    RequestParametersTable.table.filter(_.code === code).delete
  }

  def takeRequestParameters(code: String): Future[Option[RequestParameters]] ={
    findRequestParameters(code).map{ params =>
      deleteRequestParameters(code);
      params
    }
  }
}


object  RequestParametersTable {
  val table = TableQuery[RequestParametersTable]
}

class  RequestParametersTable(tag: Tag)
  extends Table[RequestParameters](tag,"request_parameters"){

  def code = column[String]("code", PrimaryKey)
  def username = column[String]("username")
  def clientId = column[String]("client_id")
  def responseType = column[String]("response_type")
  def state = column[String]("state")
  def redirectUri = column[String]("redirect_uri")
  def scope = column[String]("scope")
  def created = column[Option[Timestamp]]("created", SqlType("timestamp default CURRENT_TIMESTAMP"))

  override def * =
    (code, username, clientId, responseType, state, redirectUri, scope, created) <> (
      (RequestParameters.apply _).tupled,
      RequestParameters.unapply
    )
}

