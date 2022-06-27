import akka.actor.ActorSystem
import akka.http.scaladsl.model.{ContentTypes, HttpEntity}
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.directives.Credentials.Provided
import akka.http.scaladsl.{ConnectionContext, Http, HttpConnectionContext}
import com.typesafe.config.{Config, ConfigFactory}
import repository.{AccountRepository, ClientRepository, RedirectUriRepository, RequestParametersRepository, TokenRepository}
import slick.jdbc.JdbcBackend
import slick.jdbc.JdbcBackend.Database

import java.io.InputStream
import java.security.{KeyStore, SecureRandom}
import javax.net.ssl.{KeyManagerFactory, SSLContext, TrustManagerFactory}
import scala.concurrent.ExecutionContextExecutor
import scala.io.StdIn


object Main extends App with ProtectedResourcesServer with AuthorizationServer {

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
  override val config: Config = ConfigFactory.load()

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
  RedirectUriRepository.runMigration(db)
  RequestParametersRepository.runMigration(db)

  // Akka-Actor
  implicit val system: ActorSystem = ActorSystem(systemName)
  implicit val ec: ExecutionContextExecutor = system.dispatcher

  // Logging
  val log = system.log

  // Start Server
  val bindingFuture =
    Http().newServerAt(host, port)
      .bind(this.authorizationServer ~ this.protectedResourcesServer)

  log.info(s"Start Server: http://${host}:${port}")

  StdIn.readLine()
  bindingFuture
    .flatMap(_.unbind())
    .onComplete(_ => system.terminate())
}
