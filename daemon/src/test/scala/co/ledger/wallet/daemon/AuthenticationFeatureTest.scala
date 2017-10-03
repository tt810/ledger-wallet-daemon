package co.ledger.wallet.daemon

import java.util.Date

import co.ledger.wallet.daemon.services.ECDSAService
import co.ledger.wallet.daemon.utils.{FixturesUtils, HexUtils}
import com.lambdaworks.codec.Base64
import com.twitter.finagle.http.Status
import com.twitter.finatra.http.EmbeddedHttpServer
import com.twitter.inject.server.{FeatureTest}
import org.bitcoinj.core.Sha256Hash

import scala.concurrent.Await
import scala.concurrent.duration.Duration
import co.ledger.wallet.daemon.async.MDCPropagatingExecutionContext.Implicits.global

class AuthenticationFeatureTest extends FeatureTest {
  override val server = new EmbeddedHttpServer(new ServerImpl)

  test("Authentication#Basic Authentication for demo users") {
    server.httpGet(path = "/status", headers = basicAuthorisationHeader("admin", "password"))
  }

  test("Authentication#Basic Authentication with wrong demo user") {
    server.httpGet(path = "/status", headers = basicAuthorisationHeader("nil", "void"), andExpect = Status.Unauthorized)
  }

  test("Authentication#Authenticate with LWD whitelisted") {
    server.httpGet(path = "/status", headers = lwdBasicAuthorisationHeader("whitelisted"))
  }

  test("Authentication#Authenticate with LWD whitelisted and valid timestamp (after now)") {
    server.httpGet(path = "/status", headers = lwdBasicAuthorisationHeader("whitelisted", new Date(new Date().getTime + 10000)))
  }

  test("Authentication#Authenticate with LWD backlisted") {
    server.httpGet(path = "/status", headers = lwdBasicAuthorisationHeader("blacklisted"), andExpect = Status.Unauthorized)
  }

  test("Authentication#Authenticate with LWD whitelisted and invalid timestamp (before now)") {
    server.httpGet(path = "/status", headers = lwdBasicAuthorisationHeader("whitelisted", new Date(new Date().getTime - 60000)), andExpect = Status.Unauthorized)
  }

  test("Authentication#Authenticate with LWD whitelisted and invalid timestamp (after now)") {
    server.httpGet(path = "/status", headers = lwdBasicAuthorisationHeader("whitelisted", new Date(new Date().getTime + 60000)), andExpect = Status.Unauthorized)
  }

  test("Authentication#Missing authorization") {
    server.httpGet(path = "/status", headers = Map[String, String](), andExpect = Status.Unauthorized)
  }

  test("Authentication#Not authorized") {
    server.httpGet(path = "/status", headers = invalidLWDAuthorisationHeader("whitelisted"), andExpect = Status.Unauthorized)
  }

  private def basicAuthorisationHeader(username: String, password: String) = Map(
    "authorization" -> s"Basic ${Base64.encode(s"$username:$password".getBytes).mkString}"
  )

  private def invalidLWDAuthorisationHeader(seedName: String) = {
    val ecdsa = server.injector.instance(classOf[ECDSAService])
    val privKey = Sha256Hash.hash(FixturesUtils.seed(seedName).getBytes)
    val pubKey = Await.result(ecdsa.computePublicKey(privKey), Duration.Inf)
    val timestamp = (new Date()).getTime / 1000
    val message = Sha256Hash.hash(s"LWD: $timestamp\n".getBytes)
    val signed = Await.result(ecdsa.sign(message, privKey), Duration.Inf)
    Map(
      "authorization" -> s"LW ${Base64.encode(s"${HexUtils.valueOf(pubKey)}:$timestamp:${HexUtils.valueOf(signed)}".getBytes).mkString}"
    )
  }

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
