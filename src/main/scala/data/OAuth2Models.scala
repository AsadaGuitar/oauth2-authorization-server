package data

import java.sql.Timestamp

object OAuth2Models {

  // Database Models
  case class Client(id: String, secret: String, created: Option[Timestamp])
  case class RedirectUri(id: Int, clientId: String, uri: String)
  case class RequestParameters(code: String, username: String, clientId: String, responseType: String,
                               state: String, redirectUri: String, scope: String, created: Option[Timestamp])
  case class BearerToken(clientId: String, accessToken: String, refreshToken: Option[String])

  // Session Data Models
  case class RequestIdParameters(requestId: String, responseType: String, state: String,
                                 redirectUri: String, scope: String)

  // Request Data
  case class TokenEndPointRequest(grantType: String, code: String)
}
