import AuthorizationServer.verifyForm
import akka.actor.ActorSystem
import akka.http.scaladsl.model.{ContentTypes, HttpEntity}
import akka.http.scaladsl.{ConnectionContext, Http, HttpConnectionContext}
import com.typesafe.config.{Config, ConfigFactory}
import repository.{AccountRepository, ClientRepository, RedirectUrlRepository, TokenRepository}
import slick.basic.DatabaseConfig
import slick.jdbc.JdbcBackend.Database
import slick.migration.api.{PostgresDialect, SqlMigration, TableMigration}

import java.io.InputStream
import java.security.{KeyStore, SecureRandom}
import javax.net.ssl.{KeyManagerFactory, SSLContext, TrustManagerFactory}
import scala.concurrent.ExecutionContextExecutor
import scala.io.StdIn


object Main extends App with ProtectedResourcesServer {

  def setHttps() ={
    val password: Array[Char] = "pekoland".toCharArray
    val ks: KeyStore = KeyStore.getInstance("PKCS12")
    val keystore: InputStream = getClass.getClassLoader.getResourceAsStream("sample.p12")
    ks.load(keystore, password)

    val keyManagerFactory: KeyManagerFactory = KeyManagerFactory.getInstance("SunX509")
    keyManagerFactory.init(ks, password)

    val tmf: TrustManagerFactory = TrustManagerFactory.getInstance("SunX509")
    tmf.init(ks)

    val sslContext: SSLContext = SSLContext.getInstance("TLS")
    sslContext.init(keyManagerFactory.getKeyManagers, tmf.getTrustManagers, new SecureRandom)
    ConnectionContext.httpsServer(sslContext)
  }

  // Get Configuration
  val config: Config = ConfigFactory.load()

  // Server Configuration
  val host: String = config.getString("http.host")
  val port: Int = config.getInt("http.port")
  val https = setHttps()
  val systemName: String = config.getString("akka.actor.system-name")

  // Database Setting
  override protected val db = Database.forConfig("db_oauth2")
  AccountRepository.runMigration(db)
  TokenRepository.runMigration(db)
  ClientRepository.runMigration(db)
  RedirectUrlRepository.runMigration(db)

  // Akka-Actor
  implicit val system: ActorSystem = ActorSystem(systemName)
  implicit val ec: ExecutionContextExecutor = system.dispatcher

  // Logging
  val log = system.log

  import akka.http.scaladsl.server.Directives._
  val server = path("html") {
    get{
      val template = verifyForm("client_id", None)
      complete(HttpEntity(ContentTypes.`text/html(UTF-8)`, template))
    }
  }

  // Start Server
  val bindingFuture =
    Http().newServerAt(host, port)
//      .enableHttps(https)
      .bind(server ~ this.protectedResourcesServer)

  log.info(s"Start Server: https://${host}:${port}")

  StdIn.readLine()
  bindingFuture
    .flatMap(_.unbind())
    .onComplete(_ => system.terminate())

}