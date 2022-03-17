package repository

import slick.ast.ColumnOption.PrimaryKey
import slick.jdbc.JdbcBackend.Database
import slick.jdbc.PostgresProfile.api._
import slick.lifted.Tag
import slick.migration.api.{PostgresDialect, SqlMigration, TableMigration}
import slick.sql.SqlProfile.ColumnOption.SqlType

import scala.concurrent.{ExecutionContext, Future}


object AccountRepository {

  implicit val dialect: PostgresDialect = new PostgresDialect

  private val init =
    TableMigration(AccountTable.table)
      .drop
      .create
      .addColumns(_.id, _.name, _.password, _.email, _.created)

  private val seed = SqlMigration(
    "INSERT INTO account(id, name, password, email) VALUES ('account_1', 'account_1_user', 'account_1_pass', 'account_1@email.com');",
    "INSERT INTO account(id, name, password, email) VALUES ('account_2', 'account_2_user', 'account_2_pass', 'account_2@email.com');"
  )

  private val migration = init & seed

  def runMigration(db: Database) = db.run(migration())
}


trait AccountRepository {

  protected val db: Database
  implicit val ec: ExecutionContext

  def createAccount(account: Account): Future[Int] = db.run(AccountTable.table += account)

  def findAllAccount(): Future[Seq[Account]] = db.run(AccountTable.table.result)

  def findAccount(id: String): Future[Option[Account]] = {
    val query = for (p <- AccountTable.table if p.id === id) yield p
    db.run(query.result).map{
      case Nil => None
      case x => Some(x.head)
    }
  }
}


case class Account(id: String, name: String, password: String, email: String, created: Option[java.sql.Date])


object AccountTable {
  val table = TableQuery[AccountTable]
}


class AccountTable(tag: Tag) extends Table[Account](tag,"account"){

  def id = column[String]("id", PrimaryKey)
  def name = column[String]("name")
  def password = column[String]("password")
  def email = column[String]("email")
  def created = column[Option[java.sql.Date]]("created", SqlType("timestamp default CURRENT_TIMESTAMP"))

  override def * =
    (id, name, password, email, created) <> ((Account.apply _).tupled, Account.unapply)
}
