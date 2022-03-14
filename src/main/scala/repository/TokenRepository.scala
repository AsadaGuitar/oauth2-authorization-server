package repository

import data.OAuth2Models.BearerToken
import slick.ast.ColumnOption.PrimaryKey
import slick.jdbc.JdbcBackend.Database
import slick.jdbc.PostgresProfile.api._
import slick.lifted.Tag
import slick.migration.api.{PostgresDialect, SqlMigration, TableMigration}

import scala.concurrent.{ExecutionContext, Future}


object TokenRepository {

  implicit val dialect: PostgresDialect = new PostgresDialect

  private val init =
    TableMigration(BearerTokenTable.table)
      .drop
      .create
      .addColumns(_.clientId,_.accessToken,_.refreshToken)

  private val seed = SqlMigration(
    "INSERT INTO bearer_token VALUES ('client_1','atoken_1','rtoken_1');",
    "INSERT INTO bearer_token VALUES ('client_2','atoken_2','rtoken_2');",
  )

  private val migration = init & seed

  def runMigration(db: Database) = db.run(migration())

}

trait TokenRepository {

  protected val db: Database
  implicit val ec: ExecutionContext

  def createToken(bearerToken: BearerToken): Future[Int] = db.run(BearerTokenTable.table += bearerToken)

  def findToken(id: String): Future[Option[BearerToken]] = {
    val query = for (p <- BearerTokenTable.table if p.clientId === id) yield p
    db.run(query.result).map{
      case Nil => None
      case x => Some(x.head)
    }
  }
}


object BearerTokenTable {
  val table = TableQuery[BearerTokenTable]
}

class BearerTokenTable(tag: Tag) extends Table[BearerToken](tag,"bearer_token"){

  def clientId = column[String]("client_id", PrimaryKey)
  def accessToken = column[String]("access_token")
  def refreshToken = column[Option[String]]("refresh_token")

  override def * =
    (clientId,accessToken,refreshToken) <> ((BearerToken.apply _).tupled, BearerToken.unapply)
}

