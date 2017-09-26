package co.ledger.wallet.daemon.database

import java.util.concurrent.{ConcurrentHashMap, Executors}
import javax.inject.Singleton

import co.ledger.core
import co.ledger.wallet.daemon.libledger_core.async.ScalaThreadDispatcher
import co.ledger.wallet.daemon.libledger_core.crypto.SecureRandomRNG
import co.ledger.wallet.daemon.libledger_core.debug.NoOpLogPrinter
import co.ledger.wallet.daemon.libledger_core.filesystem.ScalaPathResolver
import co.ledger.wallet.daemon.libledger_core.net.{ScalaHttpClient, ScalaWebSocketClient}
import co.ledger.wallet.daemon.utils.HexUtils
import org.bitcoinj.core.Sha256Hash
import co.ledger.core.implicits._
import co.ledger.wallet.daemon.DaemonConfiguration
import co.ledger.wallet.daemon.async.SerialExecutionContext
import co.ledger.wallet.daemon.exceptions._
import slick.jdbc.JdbcBackend.Database

import scala.concurrent.Future
import collection.JavaConverters._
import scala.concurrent.ExecutionContext

/**
  * TODO: Add wallets and accounts to cache
  */
@Singleton
class DefaultDaemonCache extends DaemonCache {
  import DefaultDaemonCache._
  private val _writeContext = SerialExecutionContext.newInstance()

  def createPool(user: User, poolName: String, configuration: String): Future[core.WalletPool] = {
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

  def createWallet(walletName: String, currencyName: String, poolName: String, user: User): Future[core.Wallet] = {
    debug(s"Create wallet with params: wallet=${walletName} currency=$currencyName poolName=$poolName userPubKey=${user.pubKey}")
    getPool(user.pubKey, poolName).flatMap { corePool =>
      getCurrency(corePool.getName, currencyName).flatMap { currency =>
        corePool.createWallet(walletName, currency, core.DynamicObject.newInstance()).map { wallet =>
          debug(s"Core wallet created: $wallet")
          wallet
        }
      }
    }
  }

  def createUser(user: User): Future[Int] = {
    debug(s"Create user with params: user=$user")
    dbDao.insertUser(user).map { int =>
      userPools.put(user.pubKey, new ConcurrentHashMap[String, core.WalletPool]())
      int
    }
  }

  def deletePool(user: User, poolName: String): Future[Unit] = {
    implicit val ec = _writeContext
    debug(s"Remove pool with params: poolName=$poolName user=$user")
    // p.release() TODO once WalletPool#release exists
    dbDao.deletePool(poolName, user.id.get) map { deletedRowCount =>
      if(deletedRowCount == 0)
        throw new WalletPoolNotFoundException(poolName)
      else
        getNamedPools(user.pubKey).remove(poolName)
    }
  }

  def getCurrency(poolName: String, currencyName: String): Future[core.Currency] = Future {
    debug(s"Retrieve core currency with params: currencyName=$currencyName poolName=$poolName")
    val namedCurrencies = getNamedCurrencies(poolName)
    val currency = namedCurrencies.getOrDefault(currencyName, null)
    if(currency == null)
      throw new co.ledger.wallet.daemon.exceptions.CurrencyNotFoundException(currencyName)
    else
      currency
  }

  def getCurrencies(poolName: String): Future[Seq[core.Currency]] = Future {
    debug(s"Retrieve core currencies with params: poolName=$poolName")
    getNamedCurrencies(poolName).values().asScala.toList
  }

  def getPool(pubKey: String, poolName: String): Future[core.WalletPool] = Future {
    debug(s"Retrieve core wallet pool with params: poolName=$poolName userPubKey=$pubKey")
    val namedPools = getNamedPools(pubKey)
    val pool = namedPools.getOrDefault(poolName, null)
    if(pool == null)
      throw new WalletPoolNotFoundException(poolName)
    else
      pool
  }

  def getPools(pubKey: String): Future[Seq[core.WalletPool]] = Future {
    debug(s"Retrieve core wallet pools with params: userPubKey=$pubKey")
    getNamedPools(pubKey).values().asScala.toList
  }

  def getWallets(walletBulk: Bulk, poolName: String, pubKey: String): Future[WalletsWithCount] = {
    debug(s"Retrieve core wallet with params: (offset, bulkSize=$walletBulk poolName=$poolName userPubKey=$pubKey")
    getPool(pubKey, poolName).flatMap { corePool =>
      corePool.getWalletCount().flatMap { count =>
        corePool.getWallets(walletBulk.offset, walletBulk.bulkSize) map { wallets =>
          WalletsWithCount(count, wallets.asScala.toArray)
        }
      }
    }
  }

  def getWallet(walletName: String, poolName: String, pubKey: String): Future[core.Wallet] = {
    debug(s"Retrieve core wallet with params: walletName=$walletName poolName=$poolName userPubKey=$pubKey")
    getPool(pubKey, poolName).flatMap { corePool =>
      corePool.getWallet(walletName)
    }
  }

  def getUserFromDB(pubKey: Array[Byte]): Future[Option[User]] =  {
    dbDao.getUser(pubKey)
  }

  private def getNamedCurrencies(poolName: String): ConcurrentHashMap[String, core.Currency] = {
    val namedCurrencies = pooledCurrencies.getOrDefault(poolName, null)
    if(namedCurrencies == null)
      throw new WalletPoolNotFoundException(poolName)
    else
      namedCurrencies
  }

  private def getNamedPools(pubKey: String): ConcurrentHashMap[String, core.WalletPool] = {
    val namedPools = userPools.getOrDefault(pubKey, null)
    if(namedPools == null)
      throw new UserNotFoundException(pubKey)
    else
      namedPools
  }

}

object DefaultDaemonCache extends DaemonCache {
  implicit val ec: ExecutionContext = new SerialExecutionContext()(ExecutionContext.fromExecutor(Executors.newSingleThreadExecutor()))
  def migrateDatabase(): Future[Unit] = {
    dbDao.migrate().map(_ => ())
  }

  def initialize(): Future[Unit] = {
    info("Start initializing cache...")
    dbDao.getUsers().flatMap { users =>
      val totalPools = Future.sequence(users.map { user =>
        debug(s"Retrieve pools for user $user")
        dbDao.getPools(user.id.get).flatMap { localPools =>
          val corePools = localPools.map { localPool =>
            debug(s"Retrieve pool $localPool for user $user")
            addToCache(user, localPool)
          }
          Future.sequence(corePools)
        }
      })
      totalPools.map(_.flatten)
    }
  }

  private def poolIdentifier(userId: Long, poolName: String): String = HexUtils.valueOf(Sha256Hash.hash(s"${userId}:${poolName}".getBytes))

  private def buildPool(pool: Pool): Future[core.WalletPool] = {
    val identifier = poolIdentifier(pool.userId, pool.name)
    core.WalletPoolBuilder.createInstance()
      .setHttpClient(new ScalaHttpClient)
      .setWebsocketClient(new ScalaWebSocketClient)
      .setLogPrinter(new NoOpLogPrinter(dispatcher.getMainExecutionContext))
      .setThreadDispatcher(dispatcher)
      .setPathResolver(new ScalaPathResolver(identifier))
      .setRandomNumberGenerator(new SecureRandomRNG)
      .setDatabaseBackend(core.DatabaseBackend.getSqlite3Backend)
      .setConfiguration(core.DynamicObject.newInstance())
      .setName(pool.name)
      .build()
  }

  private def addToCache(user: User, pool: Pool): Future[core.WalletPool] = {

    buildPool(pool).map { p =>
      debug(s"Built core wallet pool: poolName=${p.getName} userId=${pool.userId}")
      val namedPools = userPools.getOrDefault(user.pubKey, new ConcurrentHashMap[String, core.WalletPool]())
      // Add wallet pool to cache
      namedPools.put(pool.name, p)
      userPools.put(user.pubKey, namedPools)
      // Add currencies to cache TODO: remove this part after create currency function is supported
      p.getCurrencies().map { currencies =>
        debug(s"Retrieve currencies for wallet pool walletPool=${p.getName} currenciesSize=${currencies.size}")
        currencies.forEach(addToCache(_, p))
      }
      p
    }
  }

  private def addToCache(currency: core.Currency, pool: core.WalletPool): Unit = {
    val crcies = pooledCurrencies.getOrDefault(pool.getName, new ConcurrentHashMap[String, core.Currency]())
    crcies.put(currency.getName, currency)
    pooledCurrencies.put(pool.getName, crcies)
    debug(s"Currency added to cache currency=${currency.getName} walletPool=${pool.getName}")
  }

  private val dbDao             =   new DatabaseDao(Database.forConfig(DaemonConfiguration.dbProfileName))
  private val dispatcher        =   new ScalaThreadDispatcher(scala.concurrent.ExecutionContext.Implicits.global)
  private val pooledCurrencies  =   new ConcurrentHashMap[String, ConcurrentHashMap[String, core.Currency]]()
  private val userPools         =   new ConcurrentHashMap[String, ConcurrentHashMap[String, core.WalletPool]]()
}

case class Bulk(offset: Int = 0, bulkSize: Int = 20)
case class WalletsWithCount(count: Int, wallets: Array[core.Wallet])