package co.ledger.wallet.daemon.database

import java.util.concurrent.{ConcurrentHashMap, Executors}
import javax.inject.Singleton

import co.ledger.core
import co.ledger.core.{Account, AccountCreationInfo, implicits}
import co.ledger.wallet.daemon.libledger_core.async.ScalaThreadDispatcher
import co.ledger.wallet.daemon.libledger_core.crypto.SecureRandomRNG
import co.ledger.wallet.daemon.libledger_core.debug.NoOpLogPrinter
import co.ledger.wallet.daemon.libledger_core.filesystem.ScalaPathResolver
import co.ledger.wallet.daemon.libledger_core.net.{ScalaHttpClient, ScalaWebSocketClient}
import co.ledger.wallet.daemon.utils.{AsArrayList, HexUtils}
import org.bitcoinj.core.Sha256Hash
import co.ledger.core.implicits._
import co.ledger.wallet.daemon.{DaemonConfiguration, exceptions}
import co.ledger.wallet.daemon.async.SerialExecutionContext
import co.ledger.wallet.daemon.exceptions._
import co.ledger.wallet.daemon.exceptions.CurrencyNotFoundException
import co.ledger.wallet.daemon.models.AccountDerivation
import co.ledger.wallet.daemon.services.LogMsgMaker
import slick.jdbc.JdbcBackend.Database

import scala.concurrent.Future
import collection.JavaConverters._
import scala.concurrent.ExecutionContext


/**
  * TODO: Add wallets and accounts to cache
  */
@Singleton
class DefaultDaemonCache extends DaemonCache {
  implicit def asArrayList[T](input: Seq[T]) = new AsArrayList[T](input)
  import DefaultDaemonCache._
  private val _writeContext = SerialExecutionContext.newInstance()

  def createAccount(accountDerivations: AccountDerivation, user: User, poolName: String, walletName: String): Future[Account] = {
    debug(LogMsgMaker.newInstance("Creating account")
      .append("derivations", accountDerivations)
      .append("walletName", walletName)
      .append("poolName", poolName)
      .append("userPubKey", user.pubKey)
      .toString())
    getWallet(walletName, poolName, user.pubKey).flatMap { wallet =>
      val derivations = accountDerivations.derivations
      val accountCreationInfo = new AccountCreationInfo(
        accountDerivations.accountIndex,
        (for (derivationResult <- derivations) yield derivationResult.owner).asArrayList,
        (for (derivationResult <- derivations) yield derivationResult.path).asArrayList,
        (for (derivationResult <- derivations) yield HexUtils.valueOf(derivationResult.pubKey.get)).asArrayList,
        (for (derivationResult <- derivations) yield HexUtils.valueOf(derivationResult.chainCode.get)).asArrayList
      )
      wallet.newAccountWithInfo(accountCreationInfo).map { account =>
        debug(LogMsgMaker.newInstance("Account created")
          .append("derivations", accountDerivations)
          .append("walletName", walletName)
          .append("poolName", poolName)
          .append("userPubKey", user.pubKey)
          .append("result", account)
          .toString())
        account
      }
    }
  }

  def createPool(user: User, poolName: String, configuration: String): Future[core.WalletPool] = {
    implicit val ec = _writeContext
    debug(LogMsgMaker.newInstance("Creating wallet pool")
      .append("poolName", poolName)
      .append("configuration", configuration)
      .toString())
    val newPool = Pool(poolName, user.id.get, configuration)

    dbDao.insertPool(newPool).map { (_) =>
      addToCache(user,newPool).map { walletPool =>
        debug(LogMsgMaker.newInstance("Wallet pool created")
          .append("poolName", poolName)
          .append("userPubKey", user.pubKey)
          .append("result", newPool)
          .toString())
        walletPool
      }
    }.recover {
      case e: WalletPoolAlreadyExistException => {
        warn(LogMsgMaker.newInstance("Wallet pool already exist")
          .append("poolName", poolName)
          .append("userPubKey", user.pubKey)
          .append("message", e.getMessage)
          .toString())
        getPool(user.pubKey, poolName)
      }
    }.flatten
  }

  def createWallet(walletName: String, currencyName: String, poolName: String, user: User): Future[core.Wallet] = {
    debug(LogMsgMaker.newInstance("Creating wallet")
      .append("walletName", walletName)
      .append("currencyName", currencyName)
      .append("poolName", poolName)
      .append("userPubKey", user.pubKey)
      .toString())
    getPool(user.pubKey, poolName).flatMap { corePool =>
      getCurrency(corePool.getName, currencyName).flatMap { currency =>
        val coreW = corePool.createWallet(walletName, currency, core.DynamicObject.newInstance()).map { wallet =>
          debug(LogMsgMaker.newInstance("Wallet created")
            .append("walletName", walletName)
            .append("currencyName", currencyName)
            .append("poolName", poolName)
            .append("userPubKey", user.pubKey)
            .append("result", wallet)
            .toString())
          Future.successful(wallet)
        }.recover {
          case e: WalletAlreadyExistsException => {
            warn(LogMsgMaker.newInstance("Wallet already exist")
              .append("walletName", walletName)
              .append("currencyName", currencyName)
              .append("poolName", poolName)
              .append("userPubKey", user.pubKey)
              .append("message", e.getMessage)
              .toString())
            corePool.getWallet(walletName)
          }
        }
        coreW.flatten
      }
    }
  }

  def createUser(user: User): Future[Int] = {
    dbDao.insertUser(user).map { int =>
      userPools.put(user.pubKey, new ConcurrentHashMap[String, core.WalletPool]())
      debug(LogMsgMaker.newInstance("User created")
        .append("userPubKey", user.pubKey)
        .toString())
      int
    }
  }

  def deletePool(user: User, poolName: String): Future[Unit] = {
    implicit val ec = _writeContext
    debug(LogMsgMaker.newInstance("Deleting wallet pool")
      .append("poolName", poolName)
      .append("userPubKey", user.pubKey)
      .toString())
    // p.release() TODO once WalletPool#release exists
    dbDao.deletePool(poolName, user.id.get) map { deletedRowCount =>
      debug(LogMsgMaker.newInstance("Wallet pool deleted")
        .append("poolName", poolName)
        .append("userPubKey", user.pubKey)
        .append("deleteRow", deletedRowCount)
        .toString())
      getNamedPools(user.pubKey).remove(poolName)
    }
  }

  def getAccount(accountIndex: Int, pubKey: String, poolName: String, walletName: String): Future[core.Account] = {
    getWallet(walletName, poolName, pubKey).flatMap { wallet =>
      wallet.getAccount(accountIndex).map { account =>
        debug(LogMsgMaker.newInstance("Retrieved account")
          .append("accountIndex", accountIndex)
          .append("walletName", walletName)
          .append("poolName", poolName)
          .append("userPubKey", pubKey)
          .append("result", account)
          .toString())
        account
      }
    }
  }

  def getAccounts(pubKey: String, poolName: String, walletName: String): Future[Seq[core.Account]] = {
    getWallet(walletName, poolName, pubKey).flatMap { wallet =>
      wallet.getAccountCount() flatMap { (count) =>
        debug(LogMsgMaker.newInstance("Retrieved total accounts count")
          .append("walletName", walletName)
          .append("poolName", poolName)
          .append("userPubKey", pubKey)
          .append("result", count)
          .toString())
        wallet.getAccounts(0, Int.MaxValue) map { (accounts) =>
          debug(LogMsgMaker.newInstance("Retrieved accounts")
            .append("offset", 0)
            .append("bulkSize", Int.MaxValue)
            .append("walletName", walletName)
            .append("poolName", poolName)
            .append("userPubKey", pubKey)
            .append("resultSize", accounts.size())
            .toString())
          accounts.asScala.toSeq
        }
      }
    }
  }

  def getCurrency(poolName: String, currencyName: String): Future[core.Currency] = Future {
    val namedCurrencies = getNamedCurrencies(poolName)
    val currency = namedCurrencies.getOrDefault(currencyName, null)
    debug(LogMsgMaker.newInstance("Retrieved currency")
      .append("currencyName", currencyName)
      .append("poolName", poolName)
      .append("result", currency)
      .toString())
    if(currency == null)
      throw new CurrencyNotFoundException(currencyName)
    else
      currency
  }

  def getCurrencies(poolName: String): Future[Seq[core.Currency]] = Future {
    debug(LogMsgMaker.newInstance("Retrieving currencies")
      .append("poolName", poolName)
      .toString())
    getNamedCurrencies(poolName).values().asScala.toList
  }

  def getPool(pubKey: String, poolName: String): Future[core.WalletPool] = Future {
    val namedPools = getNamedPools(pubKey)
    val pool = namedPools.getOrDefault(poolName, null)
    debug(LogMsgMaker.newInstance("Retrieved wallet pool")
      .append("poolName", poolName)
      .append("userPubKey", pubKey)
      .append("result", pool)
      .toString())
    if(pool == null)
      throw new WalletPoolNotFoundException(poolName)
    else
      pool
  }

  def getPools(pubKey: String): Future[Seq[core.WalletPool]] = Future {
    getNamedPools(pubKey).values().asScala.toList
  }

  def getWallets(walletBulk: Bulk, poolName: String, pubKey: String): Future[WalletsWithCount] = {
    getPool(pubKey, poolName).flatMap { corePool =>
      corePool.getWalletCount().flatMap { count =>
        debug(LogMsgMaker.newInstance("Retrieved total wallets count")
          .append("offset", walletBulk.offset)
          .append("bulkSize", walletBulk.bulkSize)
          .append("poolName", poolName)
          .append("userPubKey", pubKey)
          .append("result", count)
          .toString())
        corePool.getWallets(walletBulk.offset, walletBulk.bulkSize) map { wallets =>
          debug(LogMsgMaker.newInstance("Retrieved wallets")
            .append("offset", walletBulk.offset)
            .append("bulkSize", walletBulk.bulkSize)
            .append("poolName", poolName)
            .append("userPubKey", pubKey)
            .append("resultSize", wallets.size())
            .toString())
          WalletsWithCount(count, wallets.asScala.toSeq)
        }
      }
    }
  }

  def getWallet(walletName: String, poolName: String, pubKey: String): Future[core.Wallet] = {
    getPool(pubKey, poolName).flatMap { corePool =>
      corePool.getWallet(walletName).map {wallet =>
        debug(LogMsgMaker.newInstance("Retrieved wallet")
          .append("walletName", walletName)
          .append("poolName", poolName)
          .append("userPubKey", pubKey)
          .append("result", wallet)
          .toString())
        wallet
      }.recover {
        case e: implicits.WalletNotFoundException => throw new exceptions.WalletNotFoundException(walletName)
      }
    }
  }

  def getUserFromDB(pubKey: Array[Byte]): Future[Option[User]] =  {
    dbDao.getUser(pubKey)
  }

  private def getNamedCurrencies(poolName: String): ConcurrentHashMap[String, core.Currency] = {
    val namedCurrencies = pooledCurrencies.getOrDefault(poolName, null)
    debug(LogMsgMaker.newInstance("Retrieved currencies")
      .append("poolName", poolName)
      .append("result", namedCurrencies)
      .toString())
    if(namedCurrencies == null)
      throw new WalletPoolNotFoundException(poolName)
    else
      namedCurrencies
  }

  private def getNamedPools(pubKey: String): ConcurrentHashMap[String, core.WalletPool] = {
    val namedPools = userPools.getOrDefault(pubKey, null)
    debug(LogMsgMaker.newInstance("Retrieved wallet pools")
      .append("userPubKey", pubKey)
      .append("resultSize", namedPools)
      .toString())
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
      debug(LogMsgMaker.newInstance("Retrieved users")
        .append("resultSize", users.size)
        .toString())
      val totalPools = Future.sequence(users.map { user =>
        dbDao.getPools(user.id.get).flatMap { localPools =>
          val corePools = localPools.map { localPool =>
            debug(LogMsgMaker.newInstance("Retrieved wallet pool")
              .append("user", user)
              .append("result", localPool)
              .toString())
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
      debug(LogMsgMaker.newInstance("Built core wallet pool")
        .append("poolName", p.getName)
        .append("userId", pool.userId)
        .append("result", p)
        .toString())
      val namedPools = userPools.getOrDefault(user.pubKey, new ConcurrentHashMap[String, core.WalletPool]())
      // Add wallet pool to cache
      namedPools.put(pool.name, p)
      userPools.put(user.pubKey, namedPools)
      // Add currencies to cache TODO: remove this part after create currency function is supported
      p.getCurrencies().map { currencies =>
        debug(LogMsgMaker.newInstance("Retrieved currencies from core")
          .append("poolName", p.getName)
          .append("resultSize", currencies.size())
          .toString())
        currencies.forEach(addToCache(_, p))
      }
      p
    }
  }

  private def addToCache(currency: core.Currency, pool: core.WalletPool): Unit = {
    val crcies = pooledCurrencies.getOrDefault(pool.getName, new ConcurrentHashMap[String, core.Currency]())
    crcies.put(currency.getName, currency)
    pooledCurrencies.put(pool.getName, crcies)
    debug(LogMsgMaker.newInstance("Added currency to cache")
      .append("currencyName", currency.getName)
      .append("poolName", pool.getName)
      .toString())
  }

  private val dbDao             =   new DatabaseDao(Database.forConfig(DaemonConfiguration.dbProfileName))
  private val dispatcher        =   new ScalaThreadDispatcher(scala.concurrent.ExecutionContext.Implicits.global)
  private val pooledCurrencies  =   new ConcurrentHashMap[String, ConcurrentHashMap[String, core.Currency]]()
  private val userPools         =   new ConcurrentHashMap[String, ConcurrentHashMap[String, core.WalletPool]]()
}

case class Bulk(offset: Int = 0, bulkSize: Int = 20)
case class WalletsWithCount(count: Int, wallets: Seq[core.Wallet])