package co.ledger.wallet.daemon.database

import java.util.concurrent.ConcurrentHashMap
import javax.inject.Singleton

import co.ledger.core
import co.ledger.core.{AccountCreationInfo, implicits}
import co.ledger.wallet.daemon.libledger_core.async.ScalaThreadDispatcher
import co.ledger.wallet.daemon.libledger_core.crypto.SecureRandomRNG
import co.ledger.wallet.daemon.libledger_core.debug.NoOpLogPrinter
import co.ledger.wallet.daemon.libledger_core.filesystem.ScalaPathResolver
import co.ledger.wallet.daemon.libledger_core.net.{ScalaHttpClient, ScalaWebSocketClient}
import co.ledger.wallet.daemon.utils.{AsArrayList, HexUtils}
import org.bitcoinj.core.Sha256Hash
import co.ledger.core.implicits._
import co.ledger.wallet.daemon.{DaemonConfiguration, exceptions, models}
import co.ledger.wallet.daemon.async.{MDCPropagatingExecutionContext, SerialExecutionContext}
import co.ledger.wallet.daemon.exceptions._
import co.ledger.wallet.daemon.exceptions.CurrencyNotFoundException
import co.ledger.wallet.daemon.exceptions.InvalidArgumentException
import co.ledger.wallet.daemon.models._
import co.ledger.wallet.daemon.services.LogMsgMaker
import com.twitter.inject.Logging
import slick.jdbc.JdbcBackend.Database

import scala.concurrent.Future
import collection.JavaConverters._
import scala.concurrent.ExecutionContext

/**
  * TODO: Add wallets and accounts to cache
  */
@Singleton
class DefaultDaemonCache() extends DaemonCache with Logging {
  implicit def asArrayList[T](input: Seq[T]) = new AsArrayList[T](input)
  implicit val ec: ExecutionContext = MDCPropagatingExecutionContext.Implicits.global
  import DefaultDaemonCache._
  private val _writeContext = SerialExecutionContext.Implicits.global

  def createAccount(accountDerivations: AccountDerivation, user: User, poolName: String, walletName: String): Future[models.Account] = {
    info(LogMsgMaker.newInstance("Creating account")
      .append("derivations", accountDerivations)
      .append("wallet_name", walletName)
      .append("pool_name", poolName)
      .append("user_pub_key", user.pubKey)
      .toString())
    getCoreWallet(walletName, poolName, user.pubKey).flatMap { wallet =>
      val derivations = accountDerivations.derivations
      val accountCreationInfo = new AccountCreationInfo(
        accountDerivations.accountIndex,
        (for (derivationResult <- derivations) yield derivationResult.owner).asArrayList,
        (for (derivationResult <- derivations) yield derivationResult.path).asArrayList,
        (for (derivationResult <- derivations) yield HexUtils.valueOf(derivationResult.pubKey.get)).asArrayList,
        (for (derivationResult <- derivations) yield HexUtils.valueOf(derivationResult.chainCode.get)).asArrayList
      )
      wallet.newAccountWithInfo(accountCreationInfo).flatMap { account =>
        debug(LogMsgMaker.newInstance("Account created")
          .append("derivations", accountDerivations)
          .append("wallet_name", walletName)
          .append("pool_name", poolName)
          .append("user_pub_key", user.pubKey)
          .append("result", account)
          .toString())
        models.newInstance(account, wallet)
      }.recover {
        case e: implicits.InvalidArgumentException => throw new InvalidArgumentException(e.getMessage, e)
      }
    }
  }

  def createWalletPool(user: User, poolName: String, configuration: String): Future[WalletPool] = {
    implicit val ec = _writeContext
    createPool(user, poolName, configuration).flatMap(corePool => models.newInstance(corePool))
  }

  def createWallet(walletName: String, currencyName: String, poolName: String, user: User): Future[Wallet] = {
    createCoreWallet(walletName, currencyName, poolName, user).flatMap { coreWallet =>
      models.newInstance(coreWallet)
    }
  }

  def createUser(user: User): Future[Int] = {
    implicit val ec = _writeContext
    dbDao.insertUser(user).map { int =>
      userPools.put(user.pubKey, new ConcurrentHashMap[String, core.WalletPool]())
      debug(LogMsgMaker.newInstance("User created")
        .append("user_pub_key", user.pubKey)
        .toString())
      int
    }
  }

  def deleteWalletPool(user: User, poolName: String): Future[Unit] = {
    implicit val ec = _writeContext
    info(LogMsgMaker.newInstance("Deleting wallet pool")
      .append("pool_name", poolName)
      .append("user_pub_key", user.pubKey)
      .toString())
    // p.release() TODO once WalletPool#release exists
    dbDao.deletePool(poolName, user.id.get) map { deletedRowCount =>
      debug(LogMsgMaker.newInstance("Wallet pool deleted")
        .append("pool_name", poolName)
        .append("user_pub_key", user.pubKey)
        .append("delete_row", deletedRowCount)
        .toString())
      getNamedPools(user.pubKey).remove(poolName)
    }
  }

  def getAccount(accountIndex: Int, pubKey: String, poolName: String, walletName: String): Future[models.Account] = {
    getCoreAccount(accountIndex, pubKey, poolName, walletName). flatMap { coreAccountWallet =>
      models.newInstance(coreAccountWallet._1, coreAccountWallet._2)
    }
  }

  def getAccounts(pubKey: String, poolName: String, walletName: String): Future[Seq[models.Account]] = {
    getCoreWallet(walletName, poolName, pubKey).flatMap { wallet =>
      debug(LogMsgMaker.newInstance("Retrieved wallet")
        .append("wallet_name", walletName)
        .append("pool_name", poolName)
        .append("user_pub_key", pubKey)
        .append("result", wallet)
        .toString())
      getCoreAccounts(wallet).flatMap { coreAccounts =>
        Future.sequence(coreAccounts.map { coreAccount =>
          models.newInstance(coreAccount, wallet)
        })
      }
    }
  }

  def getCurrency(currencyName: String, poolName: String): Future[Currency] =
    getCoreCurrency(currencyName, poolName).map(models.newInstance(_))


  def getCurrencies(poolName: String): Future[Seq[Currency]] = {
    getCoreCurrencies(poolName).map { currencies =>
      for(currency <- currencies) yield models.newInstance(currency)
    }
  }

  def getNextAccountCreationInfo(pubKey: String, poolName: String, walletName: String, accountIndex: Option[Int]): Future[AccountDerivation] = {
    info(LogMsgMaker.newInstance("Retrieving next account creation info")
      .append("account_index", accountIndex)
      .append("wallet_name", walletName)
      .append("pool_name", poolName)
      .append("user_pub_key", pubKey)
      .toString())
    getCoreWallet(walletName, poolName, pubKey).flatMap { wallet =>
      (accountIndex match {
        case Some(i) => wallet.getAccountCreationInfo(i)
        case None => wallet.getNextAccountCreationInfo()
      }).map { accountCreationInfo =>
        models.newInstance(accountCreationInfo)
      }
    }
  }

  def getAccountOperations(accountIndex: Int, walletName: String, poolName: String, pubKey: String) = {
    info(LogMsgMaker.newInstance("Retrieving account operations")
      .append("account_index", accountIndex)
      .append("wallet_name", walletName)
      .append("pool_name", poolName)
      .append("user_pub_key", pubKey)
      .toString())
//    getCoreAccount(accountIndex, pubKey, poolName, walletName).flatMap { coreAccountWallet =>
//      coreAccountWallet._1.queryOperations().
//    }
  }

  def getWalletPool(pubKey: String, poolName: String): Future[WalletPool] = {
    getPool(pubKey, poolName).flatMap(models.newInstance(_))
  }

  def getWalletPools(pubKey: String): Future[Seq[WalletPool]] = {
    getPools(pubKey).flatMap { pools =>
      Future.sequence(pools.map(corePool => models.newInstance(corePool)))
    }
  }

  def getWallets(walletBulk: Bulk, poolName: String, pubKey: String): Future[WalletsWithCount] = {
    getPool(pubKey, poolName).flatMap { corePool =>
      corePool.getWalletCount().flatMap { count =>
        debug(LogMsgMaker.newInstance("Retrieved total wallets count")
          .append("offset", walletBulk.offset)
          .append("bulk_size", walletBulk.bulkSize)
          .append("pool_name", poolName)
          .append("user_pub_key", pubKey)
          .append("result", count)
          .toString())
        corePool.getWallets(walletBulk.offset, walletBulk.bulkSize) flatMap { wallets =>
          debug(LogMsgMaker.newInstance("Retrieved wallets")
            .append("offset", walletBulk.offset)
            .append("bulk_size", walletBulk.bulkSize)
            .append("pool_name", poolName)
            .append("user_pub_key", pubKey)
            .append("result_size", wallets.size())
            .toString())
          Future.sequence(wallets.asScala.toSeq.map(models.newInstance(_))).map (WalletsWithCount(count, _))
        }
      }
    }
  }

  def getWallet(walletName: String, poolName: String, pubKey: String): Future[Wallet] = {
    getCoreWallet(walletName, poolName, pubKey).flatMap(models.newInstance(_))
  }

  def getUserDirectlyFromDB(pubKey: Array[Byte]): Future[Option[User]] =  {
    dbDao.getUser(pubKey)
  }

  private def getCoreAccount(accountIndex: Int, pubKey: String, poolName: String, walletName: String): Future[(core.Account, core.Wallet)] = {
    getCoreWallet(walletName, poolName, pubKey).flatMap { wallet =>
      wallet.getAccount(accountIndex).map { account =>
        debug(LogMsgMaker.newInstance("Retrieved account")
          .append("account_index", accountIndex)
          .append("wallet_name", walletName)
          .append("pool_name", poolName)
          .append("user_pub_key", pubKey)
          .append("result", account)
          .toString())
        (account, wallet)
      }.recover {
        case e: implicits.AccountNotFoundException => throw new exceptions.AccountNotFoundException(accountIndex)
      }
    }
  }

  private def getCoreAccounts(wallet: core.Wallet): Future[Seq[core.Account]] = {
    wallet.getAccountCount() flatMap { (count) =>
      debug(LogMsgMaker.newInstance("Retrieved total accounts count")
        .append("wallet_name", wallet.getName)
        .append("result", count)
        .toString())
      if (count == 0) Future.successful(List[core.Account]())
      else {
        wallet.getAccounts(0, count) map { (accounts) =>
          debug(LogMsgMaker.newInstance("Retrieved accounts")
            .append("offset", 0)
            .append("bulk_size", count)
            .append("wallet_name", wallet.getName)
            .append("result_size", accounts.size())
            .toString())
          accounts.asScala.toSeq
        }
      }
    }
  }

  private def getCoreCurrencies(poolName: String): Future[Seq[core.Currency]] = Future {
    info(LogMsgMaker.newInstance("Retrieving currencies")
      .append("pool_name", poolName)
      .toString())
    getNamedCurrencies(poolName).values().asScala.toList
  }

  private def getCoreCurrency(currencyName: String, poolName: String): Future[core.Currency] = Future {
    val namedCurrencies = getNamedCurrencies(poolName)
    val currency = namedCurrencies.getOrDefault(currencyName, null)
    debug(LogMsgMaker.newInstance("Retrieved currency")
      .append("currency_name", currencyName)
      .append("pool_name", poolName)
      .append("result", currency)
      .toString())
    if(currency == null)
      throw new CurrencyNotFoundException(currencyName)
    else
      currency
  }

  private def createCoreWallet(walletName: String, currencyName: String, poolName: String, user: User): Future[core.Wallet] = {
    info(LogMsgMaker.newInstance("Creating wallet")
      .append("wallet_name", walletName)
      .append("currency_name", currencyName)
      .append("pool_name", poolName)
      .append("user_pub_key", user.pubKey)
      .toString())
    getPool(user.pubKey, poolName).flatMap { corePool =>
      getCoreCurrency(currencyName, corePool.getName).flatMap { currency =>
        val coreW = corePool.createWallet(walletName, currency, core.DynamicObject.newInstance()).map { wallet =>
          debug(LogMsgMaker.newInstance("Wallet created")
            .append("wallet_name", walletName)
            .append("currency_name", currencyName)
            .append("pool_name", poolName)
            .append("user_pub_key", user.pubKey)
            .append("result", wallet)
            .toString())
          Future.successful(wallet)
        }.recover {
          case e: WalletAlreadyExistsException => {
            warn(LogMsgMaker.newInstance("Wallet already exist")
              .append("wallet_name", walletName)
              .append("currency_name", currencyName)
              .append("pool_name", poolName)
              .append("user_pub_key", user.pubKey)
              .append("message", e.getMessage)
              .toString())
            corePool.getWallet(walletName)
          }
        }
        coreW.flatten
      }
    }
  }

  private def getCoreWallet(walletName: String, poolName: String, pubKey: String): Future[core.Wallet] = {
    getPool(pubKey, poolName).flatMap { corePool =>
      corePool.getWallet(walletName).map {wallet =>
        debug(LogMsgMaker.newInstance("Retrieved wallet")
          .append("wallet_name", walletName)
          .append("pool_name", poolName)
          .append("user_pub_key", pubKey)
          .append("result", wallet)
          .toString())
        wallet
      }.recover {
        case e: implicits.WalletNotFoundException => throw new exceptions.WalletNotFoundException(walletName)
      }
    }
  }

  private def createPool(user: User, poolName: String, configuration: String)(implicit ec: ExecutionContext): Future[core.WalletPool] = {
    info(LogMsgMaker.newInstance("Creating wallet pool")
      .append("pool_name", poolName)
      .append("user", user)
      .append("configuration", configuration)
      .toString())
    val newPool = Pool(poolName, user.id.get, configuration)

    dbDao.insertPool(newPool).map { (_) =>
      addToCache(user,newPool).map { walletPool =>
        debug(LogMsgMaker.newInstance("Wallet pool created")
          .append("pool_name", poolName)
          .append("user_pub_key", user.pubKey)
          .append("result", newPool)
          .toString())
        walletPool
      }
    }.recover {
      case e: WalletPoolAlreadyExistException => {
        warn(LogMsgMaker.newInstance("Wallet pool already exist")
          .append("pool_name", poolName)
          .append("user_pub_key", user.pubKey)
          .append("message", e.getMessage)
          .toString())
        getPool(user.pubKey, poolName)
      }
    }.flatten
  }

  private def getPool(pubKey: String, poolName: String): Future[core.WalletPool] = Future {
    val namedPools = getNamedPools(pubKey)
    val pool = namedPools.getOrDefault(poolName, null)
    debug(LogMsgMaker.newInstance("Retrieved wallet pool")
      .append("pool_name", poolName)
      .append("user_pub_key", pubKey)
      .append("result", pool)
      .toString())
    if(pool == null)
      throw new WalletPoolNotFoundException(poolName)
    else
      pool
  }

  private def getPools(pubKey: String): Future[Seq[core.WalletPool]] = Future {
    getNamedPools(pubKey).values().asScala.toList
  }

  private def getNamedCurrencies(poolName: String): ConcurrentHashMap[String, core.Currency] = {
    val namedCurrencies = pooledCurrencies.getOrDefault(poolName, null)
    debug(LogMsgMaker.newInstance("Retrieved currencies")
      .append("pool_name", poolName)
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
      .append("user_pub_key", pubKey)
      .append("result", namedPools)
      .toString())
    if(namedPools == null)
      throw new UserNotFoundException(pubKey)
    else
      namedPools
  }

}

object DefaultDaemonCache extends Logging {
  private val _singleExecuter: ExecutionContext = SerialExecutionContext.Implicits.single

  def migrateDatabase(): Future[Unit] = {
    implicit val ec = _singleExecuter
    dbDao.migrate().map(_ => ())
  }

  def initialize(): Future[Unit] = {
    implicit val ec = _singleExecuter
    info("Start initializing cache...")
    dbDao.getUsers().flatMap { users =>
      debug(LogMsgMaker.newInstance("Retrieved users")
        .append("result_size", users.size)
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

  private def buildPool(pool: Pool)(implicit ec: ExecutionContext): Future[core.WalletPool] = {
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

  private def addToCache(user: User, pool: Pool)(implicit ec: ExecutionContext): Future[core.WalletPool] = {

    buildPool(pool).map { p =>
      debug(LogMsgMaker.newInstance("Built core wallet pool")
        .append("pool_name", p.getName)
        .append("user_id", pool.userId)
        .append("result", p)
        .toString())
      val namedPools = userPools.getOrDefault(user.pubKey, new ConcurrentHashMap[String, core.WalletPool]())
      // Add wallet pool to cache
      namedPools.put(pool.name, p)
      userPools.put(user.pubKey, namedPools)
      // Add currencies to cache TODO: remove this part after create currency function is supported
      p.getCurrencies().map { currencies =>
        debug(LogMsgMaker.newInstance("Retrieved currencies from core")
          .append("pool_name", p.getName)
          .append("result_size", currencies.size())
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
      .append("currency_name", currency.getName)
      .append("pool_name", pool.getName)
      .toString())
  }

  private val dbDao             =   new DatabaseDao(Database.forConfig(DaemonConfiguration.dbProfileName))
  private val dispatcher        =   new ScalaThreadDispatcher(scala.concurrent.ExecutionContext.Implicits.global)
  private val pooledCurrencies  =   new ConcurrentHashMap[String, ConcurrentHashMap[String, core.Currency]]()
  private val userPools         =   new ConcurrentHashMap[String, ConcurrentHashMap[String, core.WalletPool]]()
}

