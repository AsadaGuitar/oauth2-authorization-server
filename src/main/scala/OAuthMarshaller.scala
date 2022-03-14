import spray.json.{DefaultJsonProtocol, RootJsonFormat}

trait OAuthMarshaller extends DefaultJsonProtocol {
  import spray.json._


  implicit val authorizationFormMarshaller: RootJsonFormat[AuthorizationForm] = jsonFormat2(AuthorizationForm)
//  implicit val approveFormMarshaller: RootJsonFormat[ApproveForm] = jsonFormat2(ApproveForm)
}
