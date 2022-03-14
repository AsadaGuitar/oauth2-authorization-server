package repository

import data.OAuth2Models.Client
import slick.ast.ColumnOption._
import slick.jdbc.JdbcBackend.Database
import slick.jdbc.PostgresProfile.api._
import slick.lifted.{TableQuery, Tag}
import slick.migration.api.{PostgresDialect, SqlMigration, TableMigration}
import slick.sql.SqlProfile.ColumnOption.SqlType

import java.sql.Timestamp
import scala.concurrent.{ExecutionContext, Future}

object ClientRepository {

  implicit val dialect: PostgresDialect = new PostgresDialect

  private val initClient =
    TableMigration(ClientTable.table)
      .drop
      .create
      .addColumns(_.id, _.secret, _.created)

  private val seedClient = SqlMigration(
    "INSERT INTO client(id, secret) VALUES ('client_1', 'client_secret_1');",
    "INSERT INTO client(id, secret) VALUES ('client_2', 'client_secret_3');"
  )

  private val migration = initClient & seedClient

  def runMigration(db: Database) = db.run(migration())
}

trait ClientRepository {

  protected val db: Database
  implicit val ec: ExecutionContext

  def createClient(client: Client): Future[Int] = db.run(
    client.created match {
      case Some(_) => ClientTable.table += client
      case None    => sqlu"INSERT INTO client(id, secret) VALUES (${client.id}, ${client.secret});"
    }
  )

  def findAllClient(): Future[Seq[Account]] = db.run(AccountTable.table.result)

  def findClient(clientId: String): Future[Option[Client]] = {
    val query = for (p <- ClientTable.table if p.id === clientId) yield p
    db.run(query.result).map{
      case Nil => None
      case x => Some(x.head)
    }
  }

}


object ClientTable {
  val table = TableQuery[ClientTable]
}


class ClientTable(tag: Tag) extends Table[Client](tag,"client"){

  def id = column[String]("id", PrimaryKey)
  def secret = column[String]("secret")
  def created = column[Option[Timestamp]]("created", SqlType("timestamp default CURRENT_TIMESTAMP"))

  override def * =
    (id, secret, created) <> ((Client.apply _).tupled, Client.unapply)
}
