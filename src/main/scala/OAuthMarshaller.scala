import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import data.OAuth2Models.TokenEndPointRequest
import spray.json.{DefaultJsonProtocol, RootJsonFormat}

trait OAuthMarshaller extends DefaultJsonProtocol with SprayJsonSupport {
  import spray.json._

  implicit val TokenEndPointRequestMarshaller: RootJsonFormat[TokenEndPointRequest] = jsonFormat2(TokenEndPointRequest)
}
