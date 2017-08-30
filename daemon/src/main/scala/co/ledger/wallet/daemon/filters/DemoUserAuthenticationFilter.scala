package co.ledger.wallet.daemon.filters

import java.nio.charset.StandardCharsets
import java.util.Date
import java.util.concurrent.TimeUnit
import javax.inject.Inject

import co.ledger.core.Secp256k1
import co.ledger.wallet.daemon.services.AuthenticationService.{AuthContext, AuthContextContext}
import co.ledger.wallet.daemon.services.ECDSAService
import com.twitter.finagle.{Service, SimpleFilter}
import com.twitter.finagle.http.{Request, Response}
import com.twitter.util.Future
import org.bitcoin.NativeSecp256k1
import org.bitcoinj.core.ECKey.ECDSASignature
import org.bitcoinj.core.Sha256Hash

import scala.collection.JavaConverters._
import scala.concurrent.Await
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global

class DemoUserAuthenticationFilter @Inject()(ecdsa: ECDSAService) extends SimpleFilter[Request, Response] {
  import co.ledger.wallet.daemon.services.AuthenticationService.AuthContextContext._
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
