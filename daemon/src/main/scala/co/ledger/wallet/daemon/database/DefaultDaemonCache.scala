package co.ledger.wallet.daemon.database

import java.util.concurrent.ConcurrentHashMap
import javax.inject.Singleton

import co.ledger.core._
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
import co.ledger.wallet.daemon.exceptions._

import scala.concurrent.{Await, Future}
import collection.JavaConverters._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.Duration

@Singleton
class DefaultDaemonCache extends DaemonCache {
  import DefaultDaemonCache._
  private val _writeContext = SerialExecutionContext.newInstance()

  def createPool(user: User, poolName: String, configuration: String): Future[WalletPool] = {
    implicit val ec = _writeContext
    debug(s"Create pool with params: poolName=$poolName user=$user configuration=$configuration")
    val newPool = Pool(poolName, user.id.get, configuration)

    dbDao.insertPool(newPool).flatMap { (_) =>
      addToCache(user,newPool).map { walletPool =>
        info(s"Finish creating pool: $newPool")
        walletPool
      }
    }
  }

  def deletePool(user: User, poolName: String): Future[Unit] = {
    implicit val ec = _writeContext
    debug(s"Remove pool with params: poolName=$poolName user=$user")
    // p.release() TODO once WalletPool#release exists
    dbDao.deletePool(poolName, user.id.get) map { deletedRowCount =>
      if(deletedRowCount == 0) throw new WalletPoolNotFoundException(poolName)
      else getNamedPools(user.pubKey).remove(poolName)
    }
  }

  def getPool(pubKey: String, poolName: String): Future[WalletPool] = Future {
    debug(s"Retrieve core wallet pool with params: poolName=$poolName userPubKey=$pubKey")
    val namedPools = getNamedPools(pubKey)
    val pool = namedPools.getOrDefault(poolName, null)
    if(pool == null) throw new WalletPoolNotFoundException(poolName)
    else pool
  }

  def getPools(pubKey: String): Future[Seq[WalletPool]] = Future {
    debug(s"Retrieve core wallet pools with params: userPubKey=$pubKey")
    getNamedPools(pubKey).values().asScala.toList
  }

  def getCurrency(poolName: String, currencyName: String): Future[Currency] = Future {
    debug(s"Retrieve core currency with params: currencyName=$currencyName poolName=$poolName")
    val namedCurrencies = getNamedCurrencies(poolName)
    val currency = namedCurrencies.getOrDefault(currencyName, null)
    if(currency == null) {

      throw new co.ledger.wallet.daemon.exceptions.CurrencyNotFoundException(currencyName)
    }
    else currency
  }

  def getCurrencies(poolName: String): Future[Seq[Currency]] = Future {
    debug(s"Retrieve core currencies with params: poolName=$poolName")
    getNamedCurrencies(poolName).values().asScala.toList
  }

  private def getNamedCurrencies(poolName: String): ConcurrentHashMap[String, Currency] = {
    val namedCurrencies = pooledCurrencies.getOrDefault(poolName, null)
    if(namedCurrencies == null) throw new WalletPoolNotFoundException(poolName)
    else namedCurrencies
  }

  private def getNamedPools(pubKey: String): ConcurrentHashMap[String, WalletPool] = {
    val namedPools = userPools.getOrDefault(pubKey, null)
    if(namedPools == null) throw new UserNotFoundException(pubKey)
    else namedPools
  }

}

object DefaultDaemonCache extends DaemonCache {

  def initialize(): Unit = {
    info("Start initializing cache...")
    val usrs = Await.result(dbDao.getUsers(), Duration.Inf)
    usrs.foreach { user =>
      debug(s"Retrieve pools for user $user")
      val poolsOfUser = Await.result(dbDao.getPools(user.id.get), Duration.Inf)
      poolsOfUser.foreach { pool =>
        debug(s"Retrieve pool $pool for user $user")
        val walletPool = Await.result(addToCache(user, pool), Duration.Inf)
        val coreCs = Await.result(walletPool.getCurrencies(), Duration.Inf)
        coreCs.forEach { currency =>
          debug(s"Caching currency ${currency.getName}")
          val currencies = pooledCurrencies.getOrDefault(walletPool.getName, new ConcurrentHashMap[String, Currency]())
          currencies.put(currency.getName, currency)
          pooledCurrencies.put(walletPool.getName, currencies)
        }
      }
    }
    info("Finish initialize cache")
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

  private def addToCache(user: User, pool: Pool): Future[WalletPool] = {
    val namedPools = userPools.getOrDefault(user.pubKey, new ConcurrentHashMap[String, WalletPool]())
    buildPool(pool).map { p =>
      debug(s"Built core wallet pool: poolName=${p.getName} userId=${pool.userId}")
      // Add wallet pool to cache
      namedPools.put(pool.name, p)
      userPools.put(user.pubKey, namedPools)
      // Add currencies to cache TODO: remove this part after create currency function is supported
      p.getCurrencies().map { currencies =>
        debug(s"Retrieve currencies for wallet pool walletPool=${p.getName} currenciesSize=${currencies.size}")
        currencies.forEach { currency =>
          val crcies = pooledCurrencies.getOrDefault(p.getName, new ConcurrentHashMap[String, Currency]())
          crcies.put(currency.getName, currency)
          pooledCurrencies.put(p.getName, crcies)
          debug(s"Currency added to cache currency=${currency.getName} walletPool=${p.getName}")
          crcies
        }
      }
      p
    }
  }

  private val pooledCurrencies = new ConcurrentHashMap[String, ConcurrentHashMap[String, Currency]]()
  private val userPools = new ConcurrentHashMap[String, ConcurrentHashMap[String, WalletPool]]()
  private val dbDao = DatabaseService.dbDao
  private val dispatcher = new ScalaThreadDispatcher(scala.concurrent.ExecutionContext.Implicits.global)
}
