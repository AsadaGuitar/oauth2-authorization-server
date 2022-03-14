package repository

import data.OAuth2Models.RedirectUri
import slick.ast.ColumnOption._
import slick.jdbc.JdbcBackend.Database
import slick.jdbc.PostgresProfile.api._
import slick.lifted.{TableQuery, Tag}
import slick.migration.api.{PostgresDialect, SqlMigration, TableMigration}

import scala.concurrent.{ExecutionContext, Future}


object RedirectUriRepository {

  implicit val dialect: PostgresDialect = new PostgresDialect

  private val initRedirectUrl =
    TableMigration(RedirectUriTable.table)
      .drop
      .create
      .addColumns(_.id, _.clientId, _.redirectUri)

  private val seedRedirectUrl = SqlMigration(
    "INSERT INTO redirect_uri(client_id, uri) VALUES ('client_1', 'https://www.pricam.net');",
    "INSERT INTO redirect_uri(client_id, uri) VALUES ('client_2', 'https://www.sample2.com');",
  )

  private val migration = initRedirectUrl & seedRedirectUrl

  def runMigration(db: Database) = db.run(migration())
}
trait RedirectUriRepository {

  protected val db: Database
  implicit val ec: ExecutionContext

  def findAllRedirectUrl(clientId: String): Future[Seq[RedirectUri]] = {
    val query = for (p <- RedirectUriTable.table if p.clientId === clientId) yield p
    db.run(query.result)
  }
  def insertRedirectUrl(redirectUrl: RedirectUri): Future[Int] = db.run(RedirectUriTable.table += redirectUrl)

}


object RedirectUriTable {
  val table = TableQuery[RedirectUriTable]
}

class RedirectUriTable(tag: Tag) extends Table[RedirectUri](tag, "redirect_uri") {

  def id = column[Int]("id", PrimaryKey, AutoInc)
  def clientId = column[String]("client_id")
  def redirectUri = column[String]("uri")

  def clientKey =
    foreignKey("client_key", clientId, ClientTable.table)(_.id, onUpdate=ForeignKeyAction.Restrict, onDelete=ForeignKeyAction.Cascade)

  override def * =
    (id,clientId,redirectUri) <> ((RedirectUri.apply _).tupled, RedirectUri.unapply)
}
