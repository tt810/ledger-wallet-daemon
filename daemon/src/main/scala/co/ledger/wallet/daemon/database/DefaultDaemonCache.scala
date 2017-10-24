package co.ledger.wallet.daemon.database

import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Singleton

import co.ledger.wallet.daemon.async.{MDCPropagatingExecutionContext, SerialExecutionContext}
import co.ledger.wallet.daemon.exceptions.{UserNotFoundException, WalletPoolAlreadyExistException, WalletPoolNotFoundException}
import co.ledger.wallet.daemon.libledger_core.async.LedgerCoreExecutionContext
import co.ledger.wallet.daemon.models.Account.{Account, Derivation}
import co.ledger.wallet.daemon.models._
import co.ledger.wallet.daemon.schedulers.observers.{NewOperationEventReceiver, SynchronizationResult}
import co.ledger.wallet.daemon.services.LogMsgMaker
import co.ledger.wallet.daemon.{DaemonConfiguration, exceptions}
import com.twitter.inject.Logging
import slick.jdbc.JdbcBackend.Database

import scala.collection.JavaConverters._
import scala.collection._
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class DefaultDaemonCache() extends DaemonCache with Logging {
  implicit val ec: ExecutionContext = MDCPropagatingExecutionContext.Implicits.global
  import DefaultDaemonCache._
  private val _writeContext = SerialExecutionContext.Implicits.global
  private val _coreExecutionContext = LedgerCoreExecutionContext.newThreadPool("account-observer-thread-pool")

  def syncOperations(): Future[Seq[SynchronizationResult]] = {
    val finalRs = for {
      pools <- userPools.values
      pool <- pools.values
    } yield pool.sync()(_coreExecutionContext)
    Future.sequence(finalRs.toSeq).map(_.flatten)
  }

  def getAccount(accountIndex: Int, pubKey: String, poolName: String, walletName: String): Future[Option[Account]] = {
    getHardWallet(pubKey, poolName, walletName).flatMap { wallet => wallet.account(accountIndex) }
  }

  def getAccounts(pubKey: String, poolName: String, walletName: String): Future[Seq[Account]] = {
    getHardWallet(pubKey, poolName, walletName).flatMap { wallet => wallet.accounts() }
  }

  def createAccount(accountDerivations: AccountDerivationView, user: UserDto, poolName: String, walletName: String): Future[Account] = {
    getHardWallet(user.pubKey, poolName, walletName).flatMap { w => w.upsertAccount(accountDerivations) }
  }

  def getCurrency(currencyName: String, poolName: String, pubKey: String): Future[Option[Currency]] = {
    getHardPool(pubKey, poolName).flatMap { pool => pool.currency(currencyName) }
  }

  def getCurrencies(poolName: String, pubKey: String): Future[Seq[Currency]] = {
    getHardPool(pubKey, poolName).flatMap { pool => pool.currencies() }
  }


  def getWallet(walletName: String, poolName: String, pubKey: String): Future[Option[Wallet]] = {
    getHardPool(pubKey, poolName).flatMap { pool => pool.wallet(walletName) }
  }


  def createWallet(walletName: String, currencyName: String, poolName: String, user: UserDto): Future[Wallet] = {
    getHardPool(user.pubKey, poolName).flatMap(_.upsertWallet(walletName, currencyName))
  }

  def getWalletPool(pubKey: String, poolName: String): Future[Option[Pool]] = Future {
    getNamedPools(pubKey).get(poolName)
  }

  def getWalletPools(pubKey: String): Future[Seq[Pool]] = Future {
    getNamedPools(pubKey).values.toSeq
  }

  def getWallets(walletBulk: Bulk, poolName: String, pubKey: String): Future[(Int, Seq[Wallet])] = {
    getHardPool(pubKey, poolName).flatMap { pool => pool.wallets(walletBulk.offset, walletBulk.bulkSize) }
  }

  def createWalletPool(user: UserDto, poolName: String, configuration: String): Future[Pool] = {
    implicit val ec = _writeContext
    val poolDto = PoolDto(poolName, user.id.get, configuration)
    dbDao.insertPool(poolDto).map { id =>
      addToCache(user, poolDto).map { pool =>
        pool.registerEventReceiver(new NewOperationEventReceiver(id, opsCache), _coreExecutionContext)
        pool
      }
    }.recover {
      case e: WalletPoolAlreadyExistException => {
        warn(LogMsgMaker.newInstance("Pool already exist").append("user", user).append("pool_name", poolName).toString())
        getHardPool(user.pubKey, poolName)
      }
    }.flatten
  }

  def deleteWalletPool(user: UserDto, poolName: String): Future[Unit] = {
    implicit val ec = _writeContext
    // p.release() TODO once WalletPool#release exists
    dbDao.deletePool(poolName, user.id.get) map { deletedPool =>
      deletedPool.flatMap { _ =>
        getNamedPools(user.pubKey).remove(poolName).map { pool =>
          pool.unregisterEventReceivers()
        }
      }
    }
  }

  private def getHardWallet(pubKey: String, poolName: String, walletName: String): Future[Wallet] = {
    getWallet(walletName, poolName, pubKey).map {
      case None => throw exceptions.WalletNotFoundException(walletName)
      case Some(w) => w
    }
  }

  private def getHardPool(pubKey: String, poolName: String): Future[Pool] = {
    getWalletPool(pubKey, poolName).map {
      case Some(pool) => pool
      case None => throw WalletPoolNotFoundException(poolName)
    }
  }

  private def getNamedPools(pubKey: String): concurrent.Map[String, Pool] = userPools.get(pubKey) match {
    case Some(pools) => pools
    case None => throw UserNotFoundException(pubKey)
  }

  def createUser(user: UserDto): Future[Long] = {
    implicit val ec = _writeContext
    dbDao.insertUser(user).map { id =>
      userPools.put(user.pubKey, new ConcurrentHashMap[String, Pool]().asScala)
      info(LogMsgMaker.newInstance("User created")
        .append("user_id", id)
        .append("user", user)
        .toString())
      id
    }
  }

  def getNextAccountCreationInfo(pubKey: String, poolName: String, walletName: String, accountIndex: Option[Int]): Future[Derivation] = {
    getHardWallet(pubKey, poolName, walletName).flatMap { wallet => wallet.accountCreationInfo(accountIndex) }
  }

  def getPreviousBatchAccountOperations(user: UserDto, accountIndex: Int, poolName: String, walletName: String, previous: UUID, fullOp: Int): Future[PackedOperationsView] = {
    getHardAccount(user.pubKey, poolName, walletName, accountIndex).flatMap { pair =>
      val previousRecord = opsCache.getPreviousOperationRecord(previous)
      pair._2.operations(previousRecord.offset(), previousRecord.batch, fullOp).flatMap { ops =>
        Future.sequence(ops.map { op => op.operationView })
          .map { os => PackedOperationsView(previousRecord.previous, previousRecord.next, os)}
      }
    }
  }


  def getNextBatchAccountOperations(user: UserDto, accountIndex: Int, poolName: String, walletName: String, next: UUID, fullOp: Int): Future[PackedOperationsView] = {
    getPoolFromDB(user.id.get, poolName).flatMap { poolDto =>
      getHardAccount(user.pubKey, poolName, walletName, accountIndex).flatMap { pair =>
        val candidate = opsCache.getOperationCandidate(next)
        pair._2.operations(candidate.offset(), candidate.batch, fullOp).flatMap { ops =>
          val realBatch = if (ops.size < candidate.batch) ops.size else candidate.batch
          val next = if (realBatch < candidate.batch) None else candidate.next
          val previous = candidate.previous
          val operationRecord = opsCache.insertOperation(candidate.id, poolDto.id.get, walletName, accountIndex, candidate.offset(), candidate.batch, next, previous)
          Future.sequence(ops.map { op => op.operationView  })
            .map { os => PackedOperationsView(operationRecord.previous, operationRecord.next, os)}
        }
      }
    }
  }

  def getAccountOperations(user: UserDto, accountIndex: Int, poolName: String, walletName: String, batch: Int, fullOp: Int): Future[PackedOperationsView] = {
    getPoolFromDB(user.id.get, poolName).flatMap { poolDto =>
      getHardAccount(user.pubKey, poolName, walletName, accountIndex).flatMap { pair =>
        val offset = 0
        pair._2.operations(offset, batch, fullOp).flatMap { ops =>
          val realBatch = if (ops.size < batch) ops.size else batch
          val next = if (realBatch < batch) None else Option(UUID.randomUUID())
          val previous = None
          val operationRecord = opsCache.insertOperation(UUID.randomUUID(), poolDto.id.get, walletName, accountIndex, offset, batch, next, previous)
          Future.sequence(ops.map { op => op.operationView })
            .map { os => PackedOperationsView(operationRecord.previous, operationRecord.next, os) }
        }
      }
    }
  }

  private def getHardAccount(pubKey: String, poolName: String, walletName: String, accountIndex: Int): Future[(Wallet, Account)] = {
    getHardWallet(pubKey, poolName, walletName).flatMap { wallet =>
      wallet.account(accountIndex).map {
        case Some(a) => (wallet, a)
        case None => throw exceptions.AccountNotFoundException(accountIndex)
      }
    }
  }

  private def getPoolFromDB(userId: Long, poolName: String): Future[PoolDto] = {
    dbDao.getPool(userId, poolName).map {
      case None => throw WalletPoolNotFoundException(poolName)
      case Some(dto) => dto
    }
  }

  def getUserDirectlyFromDB(pubKey: Array[Byte]): Future[Option[UserDto]] =  {
    dbDao.getUser(pubKey)
  }

  def getUserDirectlyFromDB(pubKey: String): Future[Option[UserDto]] = {
    dbDao.getUser(pubKey)
  }

}

object DefaultDaemonCache extends Logging {
  private val _singleExecuter: ExecutionContext = SerialExecutionContext.singleNamedThread("database-initialization-thread-pool")

  def migrateDatabase(): Future[Unit] = {
    implicit val ec: ExecutionContext = _singleExecuter
    dbDao.migrate().map(_ => ())
  }

  def initialize(): Future[Unit] = {
    implicit val ec: ExecutionContext = _singleExecuter
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

  private def addToCache(user: UserDto, pool: PoolDto)(implicit ec: ExecutionContext): Future[Pool] = {
    Pool.newCoreInstance(pool).map { p =>
      debug(LogMsgMaker.newInstance("Core wallet pool created")
        .append("user", user)
        .append("pool", pool)
        .append("result_core_pool", p)
        .toString())
      val namedPools = userPools.getOrElse(user.pubKey, new ConcurrentHashMap[String, Pool]().asScala)
      // Add wallet pool to cache
      val mPool = Pool.newInstance(p)
      namedPools.put(pool.name, mPool)
      userPools.put(user.pubKey, namedPools)
      mPool
    }
  }

  private[database] val dbDao             =   new DatabaseDao(Database.forConfig(DaemonConfiguration.dbProfileName))
  private val userPools: concurrent.Map[String, concurrent.Map[String, Pool]] =   new ConcurrentHashMap[String, concurrent.Map[String, Pool]]().asScala
  private[database] val opsCache: OperationCache = new OperationCache()
}

case class Bulk(offset: Int = 0, bulkSize: Int = 20)
