# oauth2_authorization_server @2022
Authorization Server &amp; Protected Resources Server 
## Supported Features
| Authorization End Point 1     |  Authorization End Point 2     | Token End Point                              |
| ----                          | ----                           | ----                                         |
|  validate response type.      | get form data.                 | basic authenticate client id, client secret. |
|  validate client_id.          | take params by request id.     | get request body.                            |
|  validate redirect uri.       | validate response type.        | get request param by authenticate code.      |
|  generate request id.         | authenticate user.             | vaildate client id.                          | 
|  store params in the session. | generate authorization code.   | generate jwt.                                |
|  render approve page.         | store params with code as key. | store tokens.                                |

## Using Technologies
| Server      | Database     | Database Library   | Template Engine Library | Json Mapper |
| ----        | ----         | ----               | ---                     | ----        |
| *Akka-http* | *Postgresql* | *Slick*            | *Scalate*               | Spray       |


