package co.ledger.wallet.daemon.database.caches

import java.util.concurrent.{ConcurrentHashMap, Executors}
import javax.inject.Singleton

import co.ledger.core.{DatabaseBackend, DynamicObject, WalletPoolBuilder, WalletPool => CoreWalletPool}
import co.ledger.wallet.daemon.database.{DatabaseDao, Pool, User}
import co.ledger.wallet.daemon.libledger_core.async.ScalaThreadDispatcher
import co.ledger.wallet.daemon.libledger_core.crypto.SecureRandomRNG
import co.ledger.wallet.daemon.libledger_core.debug.NoOpLogPrinter
import co.ledger.wallet.daemon.libledger_core.filesystem.ScalaPathResolver
import co.ledger.wallet.daemon.libledger_core.net.{ScalaHttpClient, ScalaWebSocketClient}
import co.ledger.wallet.daemon.services.DatabaseService
import co.ledger.wallet.daemon.utils.HexUtils
import org.bitcoinj.core.Sha256Hash

import scala.concurrent.{Await, ExecutionContext, Future}
import co.ledger.core.implicits._
import co.ledger.wallet.daemon.async.SerialExecutionContext
import co.ledger.wallet.daemon.exceptions.ResourceNotFoundException
import co.ledger.wallet.daemon.services.DatabaseService.info
import co.ledger.wallet.daemon.{DaemonConfiguration, models}
import slick.jdbc.JdbcBackend.Database

import scala.concurrent.duration.Duration
import scala.util.Try
/**
  * Is loaded on the initialization of server after finishing
  * database migration.
  * Note: cache does not have ability to update itself to reflect
  * core DB updates.
  */
@Singleton
class DefaultDaemonCache extends DaemonCache {

  def getWalletPool(poolName: String, userId: Int): Future[models.WalletPool] = {
    val p: CoreWalletPool = fPools.getOrDefault(poolIdentifier(userId, poolName), null)
    if (p != null) {
      debug(s"Pool obtained: ${p.getName}")
      models.newInstance(p)
    } else Future.failed(ResourceNotFoundException(classOf[CoreWalletPool], poolName))
  }

  def getWalletPools(userId: Int): Future[Seq[models.WalletPool]] = {
    val modelPools  = for {
      walletPoolsSeq <- dbDao.getPools(userId).map { pools =>
        debug(s"Obtain wallet pools from daemon DB: size=${pools.size}")
        pools.map(getPoolFromCache(_))
      }
      pools = walletPoolsSeq.map { walletPool => models.newInstance(walletPool)
      }} yield Future.sequence(pools)
    modelPools.flatten
  }



  private def getPoolFromCache(pool: Pool): CoreWalletPool = {
    val p = Option(fPools.get(poolIdentifier(pool)))
    if(p.isEmpty) throw ResourceNotFoundException(classOf[CoreWalletPool], pool.name)
    p.get
  }

  private def buildPool(pool: Pool): Future[CoreWalletPool] = {
    val identifier = poolIdentifier(pool)
    WalletPoolBuilder.createInstance()
      .setHttpClient(new ScalaHttpClient)
      .setWebsocketClient(new ScalaWebSocketClient)
      .setLogPrinter(new NoOpLogPrinter(fDispatcher.getMainExecutionContext))
      .setThreadDispatcher(fDispatcher)
      .setPathResolver(new ScalaPathResolver(identifier))
      .setRandomNumberGenerator(new SecureRandomRNG)
      .setDatabaseBackend(DatabaseBackend.getSqlite3Backend)
      .setConfiguration(DynamicObject.newInstance())
      .setName(pool.name)
      .build() map {(p) =>
      fPools.put(identifier, p)
      debug(s"Built core wallet pool: identifier=${identifier} poolName=${p.getName}")
      p
    }
  }

  private def poolIdentifier(pool: Pool): String = poolIdentifier(pool.userId, pool.name)
  private def poolIdentifier(userId: Long, poolName: String): String = HexUtils.valueOf(Sha256Hash.hash(s"${userId}:${poolName}".getBytes))

  private val fPools = new ConcurrentHashMap[String, CoreWalletPool]()
  private val fDispatcher = new ScalaThreadDispatcher(scala.concurrent.ExecutionContext.Implicits.global)
  private val dbDao =
}

object DefaultDaemonCache extends DaemonCache {

  def load(cache: DefaultDaemonCache): Unit = {
    info("Start loading wallet pools...")
    Try(Await.result(cache.dbDao.getPools.map(pools => pools.map(cache.buildPool)), Duration.Inf))
    info("Finish loading wallet pools...")
  }

}
