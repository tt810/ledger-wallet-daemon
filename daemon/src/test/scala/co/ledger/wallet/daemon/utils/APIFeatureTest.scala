package co.ledger.wallet.daemon.utils

import java.util.Date

import co.ledger.wallet.daemon.ServerImpl
import co.ledger.wallet.daemon.services.ECDSAService
import com.lambdaworks.codec.Base64
import com.twitter.finagle.http.Response
import com.twitter.finatra.http.EmbeddedHttpServer
import com.twitter.inject.server.FeatureTest
import org.bitcoinj.core.Sha256Hash

import scala.concurrent.Await
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.Duration

trait APIFeatureTest extends FeatureTest {
  override val server = new EmbeddedHttpServer(new ServerImpl)

  def defaultHeaders = lwdBasicAuthorisationHeader("whitelisted")
  def parse[A](response: Response)(implicit manifest: Manifest[A]): A = server.mapper.parse[A](response)

  private def lwdBasicAuthorisationHeader(seedName: String, time: Date = new Date()) = {
    val ecdsa = server.injector.instance(classOf[ECDSAService])
    val privKey = Sha256Hash.hash(FixturesUtils.seed(seedName).getBytes)
    val pubKey = Await.result(ecdsa.computePublicKey(privKey), Duration.Inf)
    val timestamp = time.getTime / 1000
    val message = Sha256Hash.hash(s"LWD: $timestamp\n".getBytes)
    val signed = Await.result(ecdsa.sign(message, privKey), Duration.Inf)
    Map(
      "authorization" -> s"LWD ${Base64.encode(s"${HexUtils.valueOf(pubKey)}:$timestamp:${HexUtils.valueOf(signed)}".getBytes).mkString}"
    )
  }
}
