import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import data.OAuth2Models.TokenEndPointRequest
import repository.Account
import spray.json.{DefaultJsonProtocol, RootJsonFormat}

import java.sql.Date
import java.text.SimpleDateFormat
import scala.util.Try

trait OAuthMarshaller extends DefaultJsonProtocol with SprayJsonSupport {
  import spray.json._

  implicit object DateFormat extends JsonFormat[java.sql.Date] {
    def write(date: java.sql.Date): JsString = JsString(dateToIsoString(date))
    def read(json: JsValue): java.sql.Date = json match {
      case JsString(rawDate) =>
        parseIsoDateString(rawDate)
          .fold(deserializationError(s"Expected ISO Date format, got $rawDate"))(identity)
      case error => deserializationError(s"Expected JsString, got $error")
    }
    private val localIsoDateFormatter = new ThreadLocal[SimpleDateFormat] {
      override def initialValue() = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ")
    }

    private def dateToIsoString(date: java.sql.Date) =
      localIsoDateFormatter.get().format(date)

    private def parseIsoDateString(date: String): Option[java.sql.Date] =
      Try{ java.sql.Date.valueOf(date) }.toOption
  }

  implicit val tokenEndPointRequestMarshaller: RootJsonFormat[TokenEndPointRequest] = jsonFormat2(TokenEndPointRequest)

  implicit val accountMarshaller: RootJsonFormat[Account] = jsonFormat5(Account)
}
