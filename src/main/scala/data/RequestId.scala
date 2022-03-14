package data

case class RequestId(requestId: String)

case class AuthorizationEndPointQueryParameters(requestId: String, responseType: String,
                                                state: String, redirectUri: String, scope: String)

