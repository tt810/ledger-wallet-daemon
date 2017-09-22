package co.ledger.wallet.daemon.database

import java.util.concurrent.ConcurrentHashMap
import javax.inject.Singleton

import co.ledger.core.{DatabaseBackend, DynamicObject, WalletPool, WalletPoolBuilder}
import co.ledger.wallet.daemon.libledger_core.async.ScalaThreadDispatcher
import co.ledger.wallet.daemon.libledger_core.crypto.SecureRandomRNG
import co.ledger.wallet.daemon.libledger_core.debug.NoOpLogPrinter
import co.ledger.wallet.daemon.libledger_core.filesystem.ScalaPathResolver
import co.ledger.wallet.daemon.libledger_core.net.{ScalaHttpClient, ScalaWebSocketClient}
import co.ledger.wallet.daemon.services.DatabaseService
import co.ledger.wallet.daemon.utils.HexUtils
import org.bitcoinj.core.Sha256Hash
import co.ledger.core.implicits._
import co.ledger.wallet.daemon.async.SerialExecutionContext
import co.ledger.wallet.daemon.exceptions.ResourceNotFoundException

import scala.concurrent.{Await, Future}
import collection.JavaConverters._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.Duration

@Singleton
class DefaultDaemonCache extends DaemonCache {
  import DefaultDaemonCache._
  private val _writeContext = SerialExecutionContext.newInstance()

  def createPool(userId: Long, poolName: String, configuration: String): Future[WalletPool] = {
    implicit val ec = _writeContext
    debug(s"Create pool with params: poolName=$poolName userId=$userId configuration=$configuration")
    val newPool = Pool(poolName, userId, configuration)

    dbDao.insertPool(newPool).flatMap { (_) =>
      addToCache(newPool).map { walletPool =>
        info(s"Finish creating pool: $newPool")
        walletPool
      }
    }
  }

  def deletePool(userId: Long, poolName: String): Future[Unit] = {
    implicit val ec = _writeContext
    debug(s"Remove pool with params: poolName=$poolName userId=$userId")
    // p.release() TODO once WalletPool#release exists
    dbDao.deletePool(poolName, userId) map { deletedRowCount =>
      if(deletedRowCount == 0) throw new ResourceNotFoundException(classOf[Pool], poolName)
      else getNamedPools(userId).map { namedPools =>
        namedPools.remove(poolName)
      }
    }
  }

  def getPool(userId: Long, poolName: String): Future[WalletPool] = {
    debug(s"Retrieve core wallet pool with params: userId=$userId poolName=$poolName")
    getNamedPools(userId).map { namedPools =>
      val pool = namedPools.getOrDefault(poolName, null)
      if(pool == null) throw new ResourceNotFoundException(classOf[WalletPool], poolName)
      else pool
    }
  }

  def getPools(userId: Long): Future[Seq[WalletPool]] = {
    debug(s"Retrieve core wallet pools with params: userId=$userId")
    getNamedPools(userId).map { namedPools =>
      namedPools.values().asScala.toList
    }
  }

  def getCurrency() = ???

  def getUser(pubKey: String) = {

  }

  private def getNamedPools(userId: Long): Future[ConcurrentHashMap[String, WalletPool]] = {
    val namedPools = userPools.getOrDefault(userId, null)
    if(namedPools == null) Future.failed(ResourceNotFoundException(classOf[User], userId.toString))
    else Future.successful(namedPools)
  }

}

object DefaultDaemonCache extends DaemonCache {
  def initialize(): Unit = {
    val usrs = Await.result(dbDao.getUsers(), Duration.Inf)
    usrs.foreach { user =>
      debug(s"Retrieve pools for user $user")
      val poolsOfUser = Await.result(dbDao.getPools(user.id.get), Duration.Inf)
      poolsOfUser.foreach { pool =>
        debug(s"Retrieve pool $pool for user $user")
        Await.result(addToCache(pool), Duration.Inf)
      }
    }
    debug("Finish initializing cache")
  }

  private def poolIdentifier(userId: Long, poolName: String): String = HexUtils.valueOf(Sha256Hash.hash(s"${userId}:${poolName}".getBytes))

  private def buildPool(pool: Pool): Future[WalletPool] = {
    val identifier = poolIdentifier(pool.userId, pool.name)
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
      .build()
  }

  private def addToCache(pool: Pool): Future[WalletPool] = {
    val namedPools = userPools.getOrDefault(pool.userId, new ConcurrentHashMap[String, WalletPool]())
    buildPool(pool).map { p =>
      debug(s"Built core wallet pool: poolName=${p.getName} userId=${pool.userId}")
      namedPools.put(pool.name, p)
      userPools.put(pool.userId, namedPools)
      p
    }
  }
  private val userPools = new ConcurrentHashMap[Long, ConcurrentHashMap[String, WalletPool]]()
  private val dbDao = DatabaseService.dbDao
  private val dispatcher = new ScalaThreadDispatcher(scala.concurrent.ExecutionContext.Implicits.global)
}
