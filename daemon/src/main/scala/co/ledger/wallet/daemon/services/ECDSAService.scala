package co.ledger.wallet.daemon.services

import javax.inject.Singleton

import co.ledger.core.Secp256k1
import io.github.andrebeat.pool._

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class ECDSAService {

  def sign(data: Array[Byte], privKey: Array[Byte])(implicit ec: ExecutionContext): Future[Array[Byte]] = Future {
    lease(_.sign(privKey, data))
  }

  def verify(data: Array[Byte], signature: Array[Byte], publicKey: Array[Byte])(implicit ec: ExecutionContext): Future[Boolean] = Future {
    lease(_.verify(data, signature, publicKey))
  }

  def computePublicKey(privateKey: Array[Byte])(implicit ec: ExecutionContext): Future[Array[Byte]] = Future {
    lease(_.computePubKey(privateKey, true))
  }

  private def lease = _secp256k1Instances.acquire()

  private lazy val _secp256k1Instances: Pool[Secp256k1] = Pool(Runtime.getRuntime.availableProcessors(), Secp256k1.newInstance)
}
