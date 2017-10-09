package co.ledger.wallet.daemon.database

import java.util.UUID
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

  def createAccount(accountDerivations: AccountDerivationView, user: UserDto, poolName: String, walletName: String): Future[models.AccountView] = {
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
      val createdAccount = wallet.newAccountWithInfo(accountCreationInfo).map { account =>
        debug(LogMsgMaker.newInstance("Account created")
          .append("derivations", accountDerivations)
          .append("wallet_name", walletName)
          .append("pool_name", poolName)
          .append("user_pub_key", user.pubKey)
          .append("result", account)
          .toString())
        Account.newView(account, wallet)
      }.recover {
        case e: implicits.InvalidArgumentException => Future.failed(new InvalidArgumentException(e.getMessage, e))
        case alreadyExist: implicits.AccountAlreadyExistsException => {
          warn(LogMsgMaker.newInstance("Account already exists")
              .append("user_pub_key", user.pubKey)
              .append("pool_name", poolName)
              .append("wallet_name", walletName)
              .append("account_index", accountDerivations.accountIndex)
            .toString())
          wallet.getAccount(accountDerivations.accountIndex).flatMap(Account.newView(_, wallet))
        }
      }
      createdAccount.flatten
    }
  }

  def createWalletPool(user: UserDto, poolName: String, configuration: String): Future[WalletPoolView] = {
    implicit val ec = _writeContext
    createPool(user, poolName, configuration).flatMap(corePool => Pool.newView(corePool))
  }

  def createWallet(walletName: String, currencyName: String, poolName: String, user: UserDto): Future[WalletView] = {
    createCoreWallet(walletName, currencyName, poolName, user).flatMap { coreWallet =>
      Wallet.newView(coreWallet)
    }
  }

  def createUser(user: UserDto): Future[Int] = {
    implicit val ec = _writeContext
    dbDao.insertUser(user).map { int =>
      userPools.put(user.pubKey, new ConcurrentHashMap[String, core.WalletPool]())
      debug(LogMsgMaker.newInstance("User created")
        .append("user_pub_key", user.pubKey)
        .toString())
      int
    }
  }

  def deleteWalletPool(user: UserDto, poolName: String): Future[Unit] = {
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

  def getAccount(accountIndex: Int, pubKey: String, poolName: String, walletName: String): Future[AccountView] = {
    getCoreAccount(accountIndex, pubKey, poolName, walletName). flatMap { coreAccountWallet =>
      Account.newView(coreAccountWallet._1, coreAccountWallet._2)
    }
  }

  def getAccounts(pubKey: String, poolName: String, walletName: String): Future[Seq[models.AccountView]] = {
    getCoreWallet(walletName, poolName, pubKey).flatMap { wallet =>
      debug(LogMsgMaker.newInstance("Retrieved wallet")
        .append("wallet_name", walletName)
        .append("pool_name", poolName)
        .append("user_pub_key", pubKey)
        .append("result", wallet)
        .toString())
      getCoreAccounts(wallet).flatMap { coreAccounts =>
        Future.sequence(coreAccounts.map { coreAccount =>
          Account.newView(coreAccount, wallet)
        })
      }
    }
  }

  def getCurrency(currencyName: String, poolName: String): Future[CurrencyView] =
    getCoreCurrency(currencyName, poolName).map(Currency.newView(_))


  def getCurrencies(poolName: String): Future[Seq[CurrencyView]] = {
    getCoreCurrencies(poolName).map { currencies =>
      for(currency <- currencies) yield Currency.newView(currency)
    }
  }

  def getNextAccountCreationInfo(pubKey: String, poolName: String, walletName: String, accountIndex: Option[Int]): Future[AccountDerivationView] = {
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
        Account.newDerivationView(accountCreationInfo)
      }
    }
  }

  def getPreviousBatchAccountOperations(user: UserDto, accountIndex: Int, poolName: String, walletName: String, previous: UUID, fullOp: Int): Future[PackedOperationsView] = {
    // fetch poolId, fetch batch and offset info from dbDao, fetch operations from lib return
    info(LogMsgMaker.newInstance("Retrieving previous batch account operations")
      .append("previous_cursor", previous)
      .append("account_index", accountIndex)
      .append("wallet_name", walletName)
      .append("pool_name", poolName)
      .append("user", user)
      .toString())
    getPoolFromDB(user.id.get, poolName).flatMap { dto =>
      dbDao.getPreviousOperationInfo(previous, user.id.get, dto.id.get, Option(walletName), Option(accountIndex)).flatMap { operationDto => operationDto match {
        case None => throw new OperationNotFoundException(previous)
        case Some(operation) => getAccountOperations(user, accountIndex, poolName, walletName, operation.offset, operation.batch, fullOp)
          .map(PackedOperationsView(operation.previous, operation.next, _))
      }}
    }
  }

  def getNextBatchAccountOperations(user: UserDto, accountIndex: Int, poolName: String, walletName: String, next: UUID, fullOp: Int): Future[PackedOperationsView] = {
    // fetch poolId, fetch batch and offset info from dbDao, fetch operations from lib, insert opsDto, return
    info(LogMsgMaker.newInstance("Retrieving next batch account operations")
      .append("next_cursor", next)
      .append("account_index", accountIndex)
      .append("wallet_name", walletName)
      .append("pool_name", poolName)
      .append("user", user)
      .toString())
    getPoolFromDB(user.id.get, poolName).flatMap { poolDto =>
      dbDao.getNextOperationInfo(next, user.id.get, poolDto.id.get, Option(walletName), Option(accountIndex)).flatMap { operationDto => operationDto match {
        case None => throw new OperationNotFoundException(next)
        case Some(operation) => {
          getAccountOperations(user, accountIndex, poolName, walletName, operation.offset, operation.batch, fullOp).flatMap { operations =>
            implicit val ec = _writeContext
            dbDao.insertOperation(operation).map { int =>
              PackedOperationsView(operation.previous, operation.next, operations)
            }
          }
        }}
      }
    }
  }

  def getAccountOperations(user: UserDto, accountIndex: Int, poolName: String, walletName: String, batch: Int, fullOp: Int): Future[PackedOperationsView] = {
    info(LogMsgMaker.newInstance("Retrieving account operations")
      .append("account_index", accountIndex)
      .append("batch", batch)
      .append("wallet_name", walletName)
      .append("pool_name", poolName)
      .append("user", user)
      .toString())
    // fetch poolId, fetch operations from lib, insert opsDto, return
    getPoolFromDB(user.id.get, poolName).flatMap { poolDto =>
      getAccountOperations(user, accountIndex, poolName, walletName, 0, batch, fullOp).flatMap { operations =>
        implicit val ec = _writeContext
        // check if total operation count less than request batch.
        val realBatch = if (operations.size < batch) operations.size else batch
        val next = if (realBatch < batch) None else Option(UUID.randomUUID())
        val operationDto = OperationDto(user.id.get, poolDto.id.get, Option(walletName), Option(accountIndex), None, 0, realBatch, next)
        dbDao.insertOperation(operationDto).map { int =>
          PackedOperationsView(None, operationDto.next, operations)
        }
      }
    }
  }

  private def getAccountOperations(user: UserDto, accountIndex: Int, poolName: String, walletName: String, offset: Long, batch: Int, fullOp: Int): Future[Seq[OperationView]] = {
    getCoreAccount(accountIndex, user.pubKey, poolName, walletName).flatMap { coreAW =>
      val coreA = coreAW._1
      val currencyName = coreAW._2.getCurrency.getName
      getOperations(coreA.queryOperations(), fullOp, offset, batch).map { ops =>
        ops.map(Operation.newView(_, currencyName, walletName, accountIndex))
      }
    }
  }

  private def getOperations(opsQuery: core.OperationQuery, fullOp: Int, offset: Long, batch: Int): Future[Seq[core.Operation]] = {
    (if (fullOp > 0)
      opsQuery.offset(offset).limit(batch).complete().execute()
    else
      opsQuery.offset(offset).limit(batch).partial().execute()
      ).map { operations =>
      val size = operations.size()
      debug(LogMsgMaker.newInstance("Retrieved operations")
        .append("offset", offset)
        .append("batch_size", batch)
        .append("result_size", size)
        .toString)
      if (size <= 0) List[core.Operation]()
      else operations.asScala.toSeq
    }
  }

  private def getPoolFromDB(userId: Long, poolName: String): Future[PoolDto] = {
    dbDao.getPool(userId, poolName).map { poolOp => poolOp match {
      case None => throw new WalletPoolNotFoundException(poolName)
      case Some(dto) => dto
    }}
  }

  def getWalletPool(pubKey: String, poolName: String): Future[WalletPoolView] = {
    getPool(pubKey, poolName).flatMap(Pool.newView(_))
  }

  def getWalletPools(pubKey: String): Future[Seq[WalletPoolView]] = {
    getPools(pubKey).flatMap { pools =>
      Future.sequence(pools.map(corePool => Pool.newView(corePool)))
    }
  }

  def getWallets(walletBulk: Bulk, poolName: String, pubKey: String): Future[WalletsViewWithCount] = {
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
          Future.sequence(wallets.asScala.toSeq.map(Wallet.newView(_))).map (WalletsViewWithCount(count, _))
        }
      }
    }
  }

  def getWallet(walletName: String, poolName: String, pubKey: String): Future[WalletView] = {
    getCoreWallet(walletName, poolName, pubKey).flatMap(Wallet.newView(_))
  }

  def getUserDirectlyFromDB(pubKey: Array[Byte]): Future[Option[UserDto]] =  {
    dbDao.getUser(pubKey)
  }

  def getUserDirectlyFromDB(pubKey: String): Future[Option[UserDto]] = {
    dbDao.getUser(pubKey)
  }


  private[daemon] def getCoreAccount(accountIndex: Int, pubKey: String, poolName: String, walletName: String): Future[(core.Account, core.Wallet)] = {
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
    getNamedCurrencies(poolName).values().asScala.toList
  }

  private def getCoreCurrency(currencyName: String, poolName: String): Future[core.Currency] = Future {
    val namedCurrencies = getNamedCurrencies(poolName)
    val currency = namedCurrencies.getOrDefault(currencyName, null)
    if(currency == null)
      throw new CurrencyNotFoundException(currencyName)
    else
      currency
  }

  private def createCoreWallet(walletName: String, currencyName: String, poolName: String, user: UserDto): Future[core.Wallet] = {
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

  private def createPool(user: UserDto, poolName: String, configuration: String)(implicit ec: ExecutionContext): Future[core.WalletPool] = {
    info(LogMsgMaker.newInstance("Creating wallet pool")
      .append("pool_name", poolName)
      .append("user", user)
      .append("configuration", configuration)
      .toString())
    val newPool = PoolDto(poolName, user.id.get, configuration)

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

  private def buildPool(pool: PoolDto)(implicit ec: ExecutionContext): Future[core.WalletPool] = {
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

  private def addToCache(user: UserDto, pool: PoolDto)(implicit ec: ExecutionContext): Future[core.WalletPool] = {

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

  private[database] val dbDao             =   new DatabaseDao(Database.forConfig(DaemonConfiguration.dbProfileName))
  private val dispatcher        =   new ScalaThreadDispatcher(MDCPropagatingExecutionContext.Implicits.global)
  private val pooledCurrencies  =   new ConcurrentHashMap[String, ConcurrentHashMap[String, core.Currency]]()
  private val userPools         =   new ConcurrentHashMap[String, ConcurrentHashMap[String, core.WalletPool]]()
}

case class Bulk(offset: Int = 0, bulkSize: Int = 20)
