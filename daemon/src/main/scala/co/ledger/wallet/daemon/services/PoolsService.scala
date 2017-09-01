package co.ledger.wallet.daemon.services

import java.sql.Timestamp
import java.util.Date
import java.util.concurrent.ConcurrentHashMap
import javax.inject.{Inject, Singleton}

import co.ledger.core.{DatabaseBackend, DynamicObject, WalletPool, WalletPoolBuilder}
import co.ledger.wallet.daemon.database.{Pool, User}

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
import co.ledger.wallet.daemon.exceptions.ResourceNotFoundException
import scala.concurrent.ExecutionContext.Implicits.global

@Singleton
class PoolsService @Inject()(databaseService: DatabaseService) {
  import PoolsService._
  private val _writeContext = SerialExecutionContext.newInstance()
  private val _readContext = scala.concurrent.ExecutionContext.Implicits.global

  def createPool(user: User, poolName: String, configuration: PoolConfiguration): Future[WalletPool] = {
    implicit val ec = _writeContext
    val newPool = Pool(0, poolName, new Timestamp(new Date().getTime), configuration.toString, "", "", user.id.get)
    databaseService.database flatMap {(db) =>
      val q = database.pools
                      .filter(pool => pool.userId === user.id.get.bind && pool.name === poolName.bind)
                      .exists.result flatMap {exists =>
        if (!exists) {
          database.pools += newPool
        } else {
          DBIO.failed(PoolAlreadyExistsException(poolName))
        }
      }
      db.run(q.transactionally)
    } flatMap {(_) =>
      buildPool(newPool)
    }
  }

  def pools(user: User): Future[Seq[WalletPool]] = {
    databaseService.database flatMap {(db) =>
      val q = for {
        p <- database.pools if p.userId === user.id.get.bind
      } yield p
      db.run(q.result)
    } flatMap {p =>
      Future.sequence(p.map(mapPool).toSeq)
    }
  }

  def pool(user: User, poolName: String): Future[WalletPool] = ???

  def removePool(user: User, poolName: String): Future[Unit] = {
    implicit val ec = _writeContext
    ???
  }

  private def mapPool(pool: Pool): Future[WalletPool] = {
    val p = _pools.getOrDefault(poolIdentifier(pool), null)
    if (p != null)
      Future.successful(p)
    else
      Future.failed(PoolNotFoundException(pool))
  }

  private def poolIdentifier(pool: Pool) = HexUtils.valueOf(Sha256Hash.hash(s"${pool.userId}:${pool.name}".getBytes))

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
      db.run(database.pools.result).flatMap {(pools) =>
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
  case class PoolAlreadyExistsException(poolName: String) extends Exception(s"Pool $poolName already exists")
  case class PoolNotFoundException(pool: Pool) extends ResourceNotFoundException(s"Pool '${}' doesn't exist")
}