package co.ledger.wallet.daemon.utils

import java.io.File
import java.util.Date

import co.ledger.wallet.daemon.ServerImpl
import co.ledger.wallet.daemon.libledger_core.filesystem.ScalaPathResolver
import co.ledger.wallet.daemon.services.ECDSAService
import com.fasterxml.jackson.databind.SerializationFeature
import com.lambdaworks.codec.Base64
import com.twitter.finagle.http.{Response, Status}
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

  def getPools(): Response = {
    server.httpGet("/pools", headers = defaultHeaders, andExpect = Status.Ok)
  }

  def getPool(poolName: String): Response = {
    getPool(poolName, Status.Ok)
  }

  def getPool(poolName: String, expected: Status): Response = {
    server.httpGet(s"/pools/$poolName", headers = defaultHeaders, andExpect = expected)
  }

  def createPool(poolName: String): Response = {
    server.httpPost(s"/pools/$poolName", "", headers = defaultHeaders, andExpect = Status.Ok)
  }

  def deletePool(poolName: String): Response = {
    server.httpDelete(s"/pools/$poolName", "", headers = defaultHeaders)
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

  protected override def beforeAll(): Unit = {
    cleanup()
  }

  protected override def afterAll(): Unit = {
    super.afterAll()
    cleanup()
  }

  private def cleanup(): Unit = {
    val directory = new ScalaPathResolver("").installDirectory
    println(s"CLEAN UP ${directory.toString}")
    for (f <- directory.listFiles()) {
      if (f.isDirectory) {
        deleteDirectory(f)
      }
    }
  }

  private def deleteDirectory(directory: File): Unit = {
    for (f <- directory.listFiles()) {
      if (f.isDirectory)
        deleteDirectory(f)
      else
        f.delete()
    }
    directory.delete()
  }

}
