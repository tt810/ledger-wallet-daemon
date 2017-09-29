package co.ledger.wallet.daemon.filters

import java.nio.charset.StandardCharsets
import java.util.Date
import javax.inject.Inject

import co.ledger.wallet.daemon.services.AuthenticationService.{AuthContext, AuthContextContext}
import co.ledger.wallet.daemon.services.ECDSAService
import com.twitter.finagle.{Service, SimpleFilter}
import com.twitter.finagle.http.{Request, Response}
import com.twitter.util.Future
import org.bitcoinj.core.Sha256Hash

import scala.concurrent.Await
import scala.concurrent.duration._
import co.ledger.wallet.daemon.async.MDCPropagatingExecutionContext.Implicits.global

class DemoUserAuthenticationFilter @Inject()(ecdsa: ECDSAService) extends SimpleFilter[Request, Response] {

  override def apply(request: Request, service: Service[Request, Response]): Future[Response] = {
    request.headerMap.get("authorization").filter(_ contains "Basic") foreach {(string) =>
      val privKey = Sha256Hash.hash(string.getBytes)
      val pk = Await.result(ecdsa.computePublicKey(privKey), 2.seconds)
      val time = new Date().getTime / 1000
      val message = Sha256Hash.hash(s"LWD: $time\n".getBytes(StandardCharsets.US_ASCII))
      val signedMessage = Await.result(ecdsa.sign(message, privKey), 2.seconds)
      AuthContextContext.setContext(request, AuthContext(pk, time, signedMessage))
    }
    service(request)
  }
}
