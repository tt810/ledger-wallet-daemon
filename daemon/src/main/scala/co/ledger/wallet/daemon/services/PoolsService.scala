package co.ledger.wallet.daemon.services

import java.util.concurrent.ConcurrentHashMap
import javax.inject.{Inject, Singleton}

import co.ledger.core.{DatabaseBackend, DynamicObject, WalletPool, WalletPoolBuilder}

import scala.concurrent.Future
import co.ledger.core.implicits._
import co.ledger.wallet.daemon.async.SerialExecutionContext
import co.ledger.wallet.daemon.libledger_core.async.ScalaThreadDispatcher
import co.ledger.wallet.daemon.libledger_core.crypto.SecureRandomRNG
import co.ledger.wallet.daemon.libledger_core.debug.NoOpLogPrinter
import co.ledger.wallet.daemon.libledger_core.filesystem.ScalaPathResolver
import co.ledger.wallet.daemon.libledger_core.net.{ScalaHttpClient, ScalaWebSocketClient}
import co.ledger.wallet.daemon.utils.HexUtils
import org.bitcoinj.core.Sha256Hash
import co.ledger.wallet.daemon.Server.profile.api._
import co.ledger.wallet.daemon.database
import co.ledger.wallet.daemon.database.{Pool, User}
import co.ledger.wallet.daemon.exceptions.ResourceNotFoundException

import scala.concurrent.ExecutionContext.Implicits.global

@Singleton
class PoolsService @Inject()(databaseService: DatabaseService) {
  import PoolsService._
  private val _writeContext = SerialExecutionContext.newInstance()
  private val _readContext = scala.concurrent.ExecutionContext.Implicits.global

  def createPool(user: User, poolName: String, configuration: PoolConfiguration): Future[WalletPool] = {
    implicit val ec = _writeContext
    val newPool = database.createPool(poolName, user.id.get, configuration.toString)
    databaseService.database flatMap {(db) =>
      db.run(database.insertPool(newPool).transactionally)
    } flatMap {(_) =>
      buildPool(newPool)
    }
  }

  def pools(user: User): Future[Seq[WalletPool]] = {
    databaseService.database flatMap {(db) =>
      db.run(database.getPools(user.id.get))
    } flatMap {p =>
      Future.sequence(p.map(mapPool).toSeq)
    }
  }

  def pool(user: User, poolName: String): Future[WalletPool] = Future {
    val p = _pools.getOrDefault(poolIdentifier(user.id.get, poolName), null)
    if (p != null)
     p
    else
      throw ResourceNotFoundException(classOf[WalletPool], poolName)
  }

  def removePool(user: User, poolName: String): Future[Unit] = {
    implicit val ec = _writeContext
    pool(user, poolName) flatMap {(p) =>
      // p.release() TODO once WalletPool#release exists
      databaseService.database flatMap {(db) =>
        db.run(database.deletePool(poolName, user.id.get))
      } map {(_) =>
        _pools.remove(poolIdentifier(user.id.get, poolName))
        ()
      }
    }
  }

  private def mapPool(pool: Pool): Future[WalletPool] = {
    val p = _pools.getOrDefault(poolIdentifier(pool), null)
    if (p != null)
      Future.successful(p)
    else
      Future.failed(ResourceNotFoundException(classOf[WalletPool], pool.name))
  }

  private def poolIdentifier(pool: Pool): String = poolIdentifier(pool.userId, pool.name)
  private def poolIdentifier(userId: Long, poolName: String): String = HexUtils.valueOf(Sha256Hash.hash(s"${userId}:${poolName}".getBytes))

  private def buildPool(pool: Pool): Future[WalletPool] = {
    val dispatcher = new ScalaThreadDispatcher(scala.concurrent.ExecutionContext.Implicits.global)
    val identifier = poolIdentifier(pool)
    WalletPoolBuilder.createInstance()
      .setHttpClient(new ScalaHttpClient)
      .setWebsocketClient(new ScalaWebSocketClient)
      .setLogPrinter(new NoOpLogPrinter(dispatcher.getMainExecutionContext))
      .setThreadDispatcher(dispatcher)
      .setPathResolver(new ScalaPathResolver(identifier))
      .setRandomNumberGenerator(new SecureRandomRNG)
      .setDatabaseBackend(DatabaseBackend.getSqlite3Backend)
      .setConfiguration(DynamicObject.newInstance())
      .setName(pool.name)
      .build() map {(p) =>
        _pools.put(identifier, p)
        p
    }
  }

  private val _pools = new ConcurrentHashMap[String, WalletPool]()

  def initialize(): Future[Unit] = {
    databaseService.database flatMap {(db) =>
      db.run(database.getPools).flatMap {(pools) =>
        Future.sequence(pools.map(buildPool))
      } map {_ =>
        ()
      }
    }
  }
}

object PoolsService {

  case class PoolConfiguration() {
    override def toString: String = ""
  }

}