package co.ledger.wallet.daemon.database

import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.annotation.Nullable
import javax.inject.Singleton

import co.ledger.core
import co.ledger.core.implicits._
import co.ledger.core.{AccountCreationInfo, implicits}
import co.ledger.wallet.daemon.async.{MDCPropagatingExecutionContext, SerialExecutionContext}
import co.ledger.wallet.daemon.clients.ClientFactory
import co.ledger.wallet.daemon.exceptions.{CurrencyNotFoundException, InvalidArgumentException, _}
import co.ledger.wallet.daemon.libledger_core.async.{LedgerCoreExecutionContext, ScalaThreadDispatcher}
import co.ledger.wallet.daemon.libledger_core.crypto.SecureRandomRNG
import co.ledger.wallet.daemon.libledger_core.debug.NoOpLogPrinter
import co.ledger.wallet.daemon.libledger_core.filesystem.ScalaPathResolver
import co.ledger.wallet.daemon.models._
import co.ledger.wallet.daemon.services.LogMsgMaker
import co.ledger.wallet.daemon.utils.{AsArrayList, HexUtils}
import co.ledger.wallet.daemon.{DaemonConfiguration, exceptions, models}
import com.twitter.inject.Logging
import org.bitcoinj.core.Sha256Hash
import slick.jdbc.JdbcBackend.Database

import scala.collection.JavaConverters._
import scala.concurrent.{ExecutionContext, Future, Promise}

/**
  * TODO: Add wallets and accounts to cache
  */
@Singleton
class DefaultDaemonCache() extends DaemonCache with Logging {
  implicit def asArrayList[T](input: Seq[T]) = new AsArrayList[T](input)
  implicit val ec: ExecutionContext = MDCPropagatingExecutionContext.Implicits.global
  import DefaultDaemonCache._
  private val _writeContext = SerialExecutionContext.Implicits.global
  private val _coreExecutionContext = LedgerCoreExecutionContext.newThreadPool("account-observer-thread-pool")

  def createAccount(accountDerivations: AccountDerivationView, user: UserDto, poolName: String, walletName: String): Future[models.AccountView] = {
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
        debug(LogMsgMaker.newInstance("Core account created")
          .append("derivations", accountDerivations)
          .append("wallet_name", walletName)
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
      createdAccount.flatten.map { account =>
        info(LogMsgMaker.newInstance("Account created")
          .append("wallet_name", walletName)
          .append("pool_name", poolName)
          .append("user", user)
          .append("derivations", accountDerivations)
          .toString())
        account
      }
    }
  }

  def createWalletPool(user: UserDto, poolName: String, configuration: String): Future[WalletPoolView] = {
    implicit val ec = _writeContext
    createPoolAndRegister(user, poolName, configuration).flatMap { corePool =>
      val view = Pool.newView(corePool)
      info(LogMsgMaker.newInstance("Wallet pool created")
        .append("user", user)
        .append("result_pool", view)
        .toString())
      view
    }
  }

  def createWallet(walletName: String, currencyName: String, poolName: String, user: UserDto): Future[WalletView] = {
    createCoreWallet(walletName, currencyName, poolName, user).flatMap { coreWallet =>
      val view = Wallet.newView(coreWallet)
      info(LogMsgMaker.newInstance("Wallet created")
        .append("user", user)
        .append("pool_name", poolName)
        .append("wallet_name", walletName)
        .append("currency_name", currencyName)
        .append("result_wallet", view)
        .toString())
      view
    }
  }

  def createUser(user: UserDto): Future[Long] = {
    implicit val ec = _writeContext
    dbDao.insertUser(user).map { id =>
      userPools.put(user.pubKey, new ConcurrentHashMap[String, core.WalletPool]())
      info(LogMsgMaker.newInstance("User created")
        .append("user_id", id)
        .append("user", user)
        .toString())
      id
    }
  }

  def deleteWalletPool(user: UserDto, poolName: String): Future[Unit] = {
    implicit val ec = _writeContext
    // p.release() TODO once WalletPool#release exists
    dbDao.deletePool(poolName, user.id.get) map { deletedPool =>
      if (deletedPool.isDefined) {
        assert(deletedPool.get.id.isDefined, "Deleted pool must contain id $deletedPool")
        @Nullable val pool: core.WalletPool = getNamedPools(user.pubKey).remove(poolName)
        if (pool != null) unregisterEventReceiver(pool, deletedPool.get.id.get)
        info(LogMsgMaker.newInstance("Deleted wallet pool")
          .append("deleted_pool", deletedPool)
          .toString())
      }
    }
  }

  def getAccount(accountIndex: Int, pubKey: String, poolName: String, walletName: String): Future[AccountView] = {
    getCoreAccount(accountIndex, pubKey, poolName, walletName). flatMap { coreAccountWallet =>
      val view = Account.newView(coreAccountWallet._1, coreAccountWallet._2)
      info(LogMsgMaker.newInstance("Retrieved account")
        .append("account_index", accountIndex)
        .append("wallet_name", walletName)
        .append("pool_name", poolName)
        .append("user_pub_key", pubKey)
        .append("result", view)
        .toString())
      view
    }
  }

  def getAccounts(pubKey: String, poolName: String, walletName: String): Future[Seq[models.AccountView]] = {
    getCoreWallet(walletName, poolName, pubKey).flatMap { wallet =>
      getCoreAccounts(wallet).flatMap { coreAccounts =>
        Future.sequence(coreAccounts.map { coreAccount =>
          Account.newView(coreAccount, wallet)
        }).map { views =>
          info(LogMsgMaker.newInstance("Retrieved accounts")
            .append("wallet_name", walletName)
            .append("pool_name", poolName)
            .append("user_pub_key", pubKey)
            .append("result", views)
            .toString())
          views
        }
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
    getCoreWallet(walletName, poolName, pubKey).flatMap { wallet =>
      (accountIndex match {
        case Some(i) => wallet.getAccountCreationInfo(i)
        case None => wallet.getNextAccountCreationInfo()
      }).map { accountCreationInfo =>
        val view = Account.newDerivationView(accountCreationInfo)
        info(LogMsgMaker.newInstance("Retrieved next account creation info")
          .append("account_index", accountIndex)
          .append("wallet_name", walletName)
          .append("pool_name", poolName)
          .append("user_pub_key", pubKey)
          .append("result", view)
          .toString())
        view
      }
    }
  }

  def getPreviousBatchAccountOperations(user: UserDto, accountIndex: Int, poolName: String, walletName: String, previous: UUID, fullOp: Int): Future[PackedOperationsView] = {
    // fetch poolId, fetch batch and offset info from dbDao, fetch operations from lib return
    getPoolFromDB(user.id.get, poolName).flatMap { dto =>
      dbDao.getPreviousOperationInfo(previous, user.id.get, dto.id.get, Option(walletName), Option(accountIndex)).flatMap { operationDto => operationDto match {
        case None => throw new OperationNotFoundException(previous)
        case Some(operation) => getAccountOperations(user, accountIndex, poolName, walletName, operation.offset, operation.batch, fullOp)
          .map { ops =>
            val view = PackedOperationsView(operation.previous, operation.next, ops)
            info(LogMsgMaker.newInstance("Retrieved previous batch account operations")
              .append("inserted_operation", operation)
              .append("result_size", view.operations.size)
              .toString())
            view
          }
      }}
    }
  }

  def getNextBatchAccountOperations(user: UserDto, accountIndex: Int, poolName: String, walletName: String, next: UUID, fullOp: Int): Future[PackedOperationsView] = {
    // fetch poolId, fetch batch and offset info from dbDao, fetch operations from lib, insert opsDto, return
    getPoolFromDB(user.id.get, poolName).flatMap { poolDto =>
      dbDao.getNextOperationInfo(next, user.id.get, poolDto.id.get, Option(walletName), Option(accountIndex)).flatMap { operationCandidate => operationCandidate match {
        case None => throw new OperationNotFoundException(next)
        case Some(opCandidate) => {
          getAccountOperations(user, accountIndex, poolName, walletName, opCandidate.offset, opCandidate.batch, fullOp).flatMap { operations =>
            implicit val ec = _writeContext
            val realBatch = if (operations.size < opCandidate.batch) operations.size else opCandidate.batch
            val next = if (realBatch < opCandidate.batch) None else opCandidate.next
            val operationDto = OperationDto(user.id.get, poolDto.id.get, Option(walletName), Option(accountIndex), opCandidate.previous, opCandidate.offset, realBatch, next)
            dbDao.insertOperation(operationDto).map { id =>
              val view = PackedOperationsView(opCandidate.previous, next, operations)
              info(LogMsgMaker.newInstance("Retrieved next batch account operations")
                .append("operation_id", id)
                .append("inserted_operation", operationDto)
                .append("result_size", view.operations.size)
                .toString())
              view
            }
          }
        }}
      }
    }
  }

  def getAccountOperations(user: UserDto, accountIndex: Int, poolName: String, walletName: String, batch: Int, fullOp: Int): Future[PackedOperationsView] = {
    // fetch poolId, fetch operations from lib, insert opsDto, return
    getPoolFromDB(user.id.get, poolName).flatMap { poolDto =>
      getAccountOperations(user, accountIndex, poolName, walletName, 0, batch, fullOp).flatMap { operations =>
        implicit val ec = _writeContext
        // check if total operation count less than request batch.
        val realBatch = if (operations.size < batch) operations.size else batch
        val next = if (realBatch < batch) None else Option(UUID.randomUUID())
        val operationDto = OperationDto(user.id.get, poolDto.id.get, Option(walletName), Option(accountIndex), None, 0, realBatch, next)
        dbDao.insertOperation(operationDto).map { id =>
          val view = PackedOperationsView(None, operationDto.next, operations)
          info(LogMsgMaker.newInstance("Retrieved account operations")
            .append("operation_id", id)
            .append("inserted_operation", operationDto)
            .append("result_size", view.operations.size)
            .toString())
          view
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
      debug(LogMsgMaker.newInstance("Core operations retrieved")
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

  def syncOperations()(implicit ec: ExecutionContext): Future[Seq[SynchronizationResult]] = {
    val finalRs = for {
      pools <- userPools.asScala.values
      pool <- pools.asScala.values
      results = for {
        count <- pool.getWalletCount()
        wallets <- pool.getWallets(0, count)
        accounts <- Future.sequence(
          wallets.asScala.toSeq.map { wallet =>
            getCoreAccounts(wallet).flatMap { accounts =>
            Future.sequence(accounts.map { account =>
              val promise: Promise[SynchronizationResult] = Promise[SynchronizationResult]()
              val receiver: core.EventReceiver = new SynchronizationEventReceiver(account.getIndex, wallet.getName, pool.getName, promise)
              account.synchronize().subscribe(_coreExecutionContext, receiver)
              promise.future
            })
          }})
      } yield accounts.flatten
    } yield results
    (Future.sequence(finalRs.toSeq)).map(_.flatten)
  }

  private def registerEventReceiver(pool: core.WalletPool, poolId: Long)(ec: ExecutionContext): core.EventReceiver = {
    val eventReceiver: core.EventReceiver = eventReceivers.getOrDefault(poolId, new NewOperationEventReceiver(poolId, dbDao)(ec))
    pool.getEventBus.subscribe(_coreExecutionContext, eventReceiver)
    debug(LogMsgMaker.newInstance("Register").append("event_receiver", eventReceiver).append("pool_id", poolId).toString())
    eventReceivers.put(poolId, eventReceiver)
  }

  private def unregisterEventReceiver(pool: core.WalletPool, poolId: Long): Unit = {
    @Nullable val eventReceiver: core.EventReceiver = eventReceivers.remove(poolId)
    debug(LogMsgMaker.newInstance("Unregister").append("event_receiver", eventReceiver).append("pool_id", poolId).toString())
    if(eventReceiver != null) pool.getEventBus.unsubscribe(eventReceiver)
  }

  private[daemon] def getCoreAccount(accountIndex: Int, pubKey: String, poolName: String, walletName: String): Future[(core.Account, core.Wallet)] = {
    getCoreWallet(walletName, poolName, pubKey).flatMap { wallet =>
      wallet.getAccount(accountIndex).map { account =>
        (account, wallet)
      }.recover {
        case e: implicits.AccountNotFoundException => throw new exceptions.AccountNotFoundException(accountIndex)
      }
    }
  }

  private def getCoreAccounts(wallet: core.Wallet): Future[Seq[core.Account]] = {
    wallet.getAccountCount() flatMap { (count) =>
      if (count == 0) Future.successful(List[core.Account]())
      else {
        wallet.getAccounts(0, count) map { (accounts) => accounts.asScala.toSeq }
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
    getPool(user.pubKey, poolName).flatMap { corePool =>
      getCoreCurrency(currencyName, corePool.getName).flatMap { currency =>
        val coreW = corePool.createWallet(walletName, currency, core.DynamicObject.newInstance()).map { wallet =>
          debug(LogMsgMaker.newInstance("Core wallet created")
            .append("wallet_name", walletName)
            .append("currency_name", currencyName)
            .append("pool_name", poolName)
            .append("user", user)
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
      corePool.getWallet(walletName).recover {
        case e: implicits.WalletNotFoundException => throw new exceptions.WalletNotFoundException(walletName)
      }
    }
  }

  private def createPoolAndRegister(user: UserDto, poolName: String, configuration: String)(implicit ec: ExecutionContext): Future[core.WalletPool] = {
    val newPool = PoolDto(poolName, user.id.get, configuration)
    dbDao.insertPool(newPool).map { poolId =>
      addToCache(user,newPool).map { walletPool =>
        registerEventReceiver(walletPool, poolId)(ec)
        walletPool
      }
    }.recover {
      case e: WalletPoolAlreadyExistException => {
        warn(LogMsgMaker.newInstance("Wallet pool already exist")
          .append("pool_name", poolName)
          .append("user", user)
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
  private val _singleExecuter: ExecutionContext = SerialExecutionContext.singleNamedThread("database-initialization-thread-pool")


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
      .setHttpClient(ClientFactory.httpClient)
      .setWebsocketClient(ClientFactory.webSocketClient)
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
      debug(LogMsgMaker.newInstance("Core wallet pool created")
        .append("user", user)
        .append("pool", pool)
        .append("result_core_pool", p)
        .toString())
      val namedPools = userPools.getOrDefault(user.pubKey, new ConcurrentHashMap[String, core.WalletPool]())
      // Add wallet pool to cache
      namedPools.put(pool.name, p)
      userPools.put(user.pubKey, namedPools)
      // Add currencies to cache TODO: remove this part after create currency function is supported
      p.getCurrencies().map { currencies => currencies.forEach(addToCache(_, p)) }
      p
    }
  }

  private def addToCache(currency: core.Currency, pool: core.WalletPool): Unit = {
    val crcies = pooledCurrencies.getOrDefault(pool.getName, new ConcurrentHashMap[String, core.Currency]())
    crcies.put(currency.getName, currency)
    pooledCurrencies.put(pool.getName, crcies)
  }

  private[database] val dbDao             =   new DatabaseDao(Database.forConfig(DaemonConfiguration.dbProfileName))
  private val dispatcher        =   new ScalaThreadDispatcher(MDCPropagatingExecutionContext.Implicits.global)
  private val pooledCurrencies  =   new ConcurrentHashMap[String, ConcurrentHashMap[String, core.Currency]]()
  private val userPools         =   new ConcurrentHashMap[String, ConcurrentHashMap[String, core.WalletPool]]()
  private val eventReceivers  = new ConcurrentHashMap[Long, core.EventReceiver]()
}

case class Bulk(offset: Int = 0, bulkSize: Int = 20)
