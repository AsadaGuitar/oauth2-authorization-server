package repository

import slick.ast.ColumnOption._
import slick.jdbc.JdbcBackend.Database
import slick.jdbc.PostgresProfile.api._
import slick.lifted.{TableQuery, Tag}
import slick.migration.api.{PostgresDialect, SqlMigration, TableMigration}

import scala.concurrent.{ExecutionContext, Future}


object RedirectUrlRepository {

  implicit val dialect: PostgresDialect = new PostgresDialect

  private val initRedirectUrl =
    TableMigration(RedirectUrlTable.table)
      .drop
      .create
      .addColumns(_.id, _.clientId, _.redirectUrl)

  private val seedRedirectUrl = SqlMigration(
    "INSERT INTO redirect_url(client_id, redirect_url) VALUES ('client_1', 'https://www.sample1.com');",
    "INSERT INTO redirect_url(client_id, redirect_url) VALUES ('client_2', 'https://www.sample2.com');",
  )

  private val migration = initRedirectUrl & seedRedirectUrl

  def runMigration(db: Database) = db.run(migration())
}
trait RedirectUrlRepository {

  protected val db: Database
  implicit val ec: ExecutionContext

  def findAllRedirectUrl(clientId: String): Future[Seq[RedirectUrl]] = {
    val query = for (p <- RedirectUrlTable.table if p.clientId === clientId) yield p
    db.run(query.result)
  }
  def insertRedirectUrl(redirectUrl: RedirectUrl): Future[Int] = db.run(RedirectUrlTable.table += redirectUrl)

}

case class RedirectUrl(id: Int, clientId: String, redirectUrl: String)

object RedirectUrlTable {
  val table = TableQuery[RedirectUrlTable]
}

class RedirectUrlTable(tag: Tag) extends Table[RedirectUrl](tag, "redirect_url") {

  def id = column[Int]("id", PrimaryKey, AutoInc)
  def clientId = column[String]("client_id")
  def redirectUrl = column[String]("redirect_url")

  def clientKey =
    foreignKey("client_key", clientId, ClientTable.table)(_.id, onUpdate=ForeignKeyAction.Restrict, onDelete=ForeignKeyAction.Cascade)

  override def * =
    (id,clientId,redirectUrl) <> ((RedirectUrl.apply _).tupled, RedirectUrl.unapply)
}
