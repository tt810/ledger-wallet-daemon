package co.ledger.wallet.daemon.services

import javax.inject.Singleton

import co.ledger.core.Secp256k1
import io.github.andrebeat.pool._

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class ECDSAService extends DaemonService {

  def sign(data: Array[Byte], privKey: Array[Byte])(implicit ec: ExecutionContext): Future[Array[Byte]] = Future {
    lease { instance =>
      debug("Sign...")
      val result = instance.sign(privKey, data)
      debug("Signed")
      result
    }
  }

  def verify(data: Array[Byte], signature: Array[Byte], publicKey: Array[Byte])(implicit ec: ExecutionContext): Future[Boolean] = Future {
    lease { instance =>
      debug("Verify....")
      val result = instance.verify(data, signature, publicKey)
      debug("Verified")
      result
    }
  }

  def computePublicKey(privateKey: Array[Byte])(implicit ec: ExecutionContext): Future[Array[Byte]] = Future {
    lease { instance =>
      debug("Computing public key...")
      val result = instance.computePubKey(privateKey, true)
      debug("Computed public key")
      result
    }
  }

  private def lease = _secp256k1Instances.acquire()

  private lazy val _secp256k1Instances: Pool[Secp256k1] = Pool(Runtime.getRuntime.availableProcessors(), Secp256k1.newInstance)
}
