package co.ledger.wallet.daemon.filters

import co.ledger.wallet.daemon.services.AuthenticationService.{AuthContext, AuthContextContext}
import co.ledger.wallet.daemon.utils.HexUtils
import com.lambdaworks.codec.Base64
import com.twitter.finagle.http.{Request, Response}
import com.twitter.finagle.{Service, SimpleFilter}
import com.twitter.util.Future

class LWDAutenticationFilter extends SimpleFilter[Request, Response] {

  private val authStringPattern = "([0-9a-fA-F]+):([0-9]+):([0-9a-fA-F]+)".r

  override def apply(request: Request, service: Service[Request, Response]): Future[Response] = {
    request.headerMap.get("authorization").filter(_ contains "LWD") foreach {(string) =>
      val auth = new String(Base64.decode(string.substring(4).toCharArray).map(_.toChar))
      val authStringPattern(pubKey, timestamp, signature) = auth
      AuthContextContext.setContext(request, AuthContext(HexUtils.valueOf(pubKey), timestamp.toLong, HexUtils.valueOf(signature)))
    }
    service(request)
  }

}
