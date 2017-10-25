package co.ledger.wallet.daemon.database

import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Singleton

import co.ledger.wallet.daemon.async.MDCPropagatingExecutionContext
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

  private val _coreExecutionContext = LedgerCoreExecutionContext.newThreadPool("account-observer-thread-pool")

  def dbMigration: Future[Unit] = {
    dbDao.migrate()
  }

  def syncOperations(): Future[Seq[SynchronizationResult]] = {
    getUsers.flatMap { usrs =>
      Future.sequence(usrs.map { user => user.sync()(_coreExecutionContext)}).map (_.flatten)
    }
  }

  def getAccount(accountIndex: Int, pubKey: String, poolName: String, walletName: String): Future[Option[Account]] = {
    getHardWallet(pubKey, poolName, walletName).flatMap { wallet => wallet.account(accountIndex) }
  }

  def getAccounts(pubKey: String, poolName: String, walletName: String): Future[Seq[Account]] = {
    getHardWallet(pubKey, poolName, walletName).flatMap { wallet => wallet.accounts() }
  }

  def createAccount(accountDerivations: AccountDerivationView, user: User, poolName: String, walletName: String): Future[Account] = {
    getHardWallet(user.pubKey, poolName, walletName).flatMap { w => w.addAccountIfNotExit(accountDerivations) }
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


  def createWallet(walletName: String, currencyName: String, poolName: String, user: User): Future[Wallet] = {
    getHardPool(user.pubKey, poolName).flatMap(_.addWalletIfNotExit(walletName, currencyName))
  }

  def getWalletPool(pubKey: String, poolName: String): Future[Option[Pool]] = {
    getHardUser(pubKey).flatMap { user => user.pool(poolName)}
  }

  def getWalletPools(pubKey: String): Future[Seq[Pool]] = {
    getHardUser(pubKey).flatMap { user =>
      user.pools().map { _.values.toSeq }
    }
  }

  def getWallets(walletBulk: Bulk, poolName: String, pubKey: String): Future[(Int, Seq[Wallet])] = {
    getHardPool(pubKey, poolName).flatMap { pool => pool.wallets(walletBulk.offset, walletBulk.bulkSize) }
  }

  def createWalletPool(user: User, poolName: String, configuration: String): Future[Pool] = {
    getHardUser(user.pubKey).flatMap { user =>
      user.addPoolIfNotExit(poolName, configuration)(_coreExecutionContext)
    }
  }

  def deleteWalletPool(user: User, poolName: String): Future[Unit] = {
    // p.release() TODO once WalletPool#release exists
    getHardUser(user.pubKey).flatMap { user =>
      user.deletePool(poolName)
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

  def getUser(pubKey: String): Future[Option[User]] = {
    if (users.contains(pubKey)) Future.successful(users.get(pubKey))
    else dbDao.getUser(pubKey).map { dto =>
      dto.map( user => users.put(pubKey, newUser(user)))
    }.map { _ => users.get(pubKey)}
  }

  private def getHardUser(pubKey: String): Future[User] = {
    getUser(pubKey).map {
      case Some(user) => user
      case None => throw UserNotFoundException(pubKey)
    }
  }

  def getUsers: Future[Seq[User]] = {
    dbDao.getUsers.map { usrs =>
      usrs.map { user =>
        if(!users.contains(user.pubKey)) users.put(user.pubKey, newUser(user))
        users(user.pubKey)
      }
    }
  }

  def createUser(user: UserDto): Future[Long] = {
    dbDao.insertUser(user).map { id =>
      users.put(user.pubKey, new User(id, user.pubKey))
      info(LogMsgMaker.newInstance("User created").append("user", users(user.pubKey)).toString())
      id
    }
  }

  def getNextAccountCreationInfo(pubKey: String, poolName: String, walletName: String, accountIndex: Option[Int]): Future[Derivation] = {
    getHardWallet(pubKey, poolName, walletName).flatMap { wallet => wallet.accountCreationInfo(accountIndex) }
  }

  def getPreviousBatchAccountOperations(user: User, accountIndex: Int, poolName: String, walletName: String, previous: UUID, fullOp: Int): Future[PackedOperationsView] = {
    getHardAccount(user.pubKey, poolName, walletName, accountIndex).flatMap { pair =>
      val previousRecord = opsCache.getPreviousOperationRecord(previous)
      pair._2.operations(previousRecord.offset(), previousRecord.batch, fullOp).flatMap { ops =>
        Future.sequence(ops.map { op => op.operationView })
          .map { os => PackedOperationsView(previousRecord.previous, previousRecord.next, os)}
      }
    }
  }


  def getNextBatchAccountOperations(user: User, accountIndex: Int, poolName: String, walletName: String, next: UUID, fullOp: Int): Future[PackedOperationsView] = {
    getPoolFromDB(user.id, poolName).flatMap { poolDto =>
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

  def getAccountOperations(user: User, accountIndex: Int, poolName: String, walletName: String, batch: Int, fullOp: Int): Future[PackedOperationsView] = {
    getPoolFromDB(user.id, poolName).flatMap { poolDto =>
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


}

object DefaultDaemonCache extends Logging {

  def newUser(user: UserDto)(implicit ec: ExecutionContext): User = {
    assert(user.id.isDefined, "User id must exist")
    new User(user.id.get, user.pubKey)
  }

  private[database] val dbDao             =   new DatabaseDao(Database.forConfig(DaemonConfiguration.dbProfileName))
  private[database] val opsCache: OperationCache = new OperationCache()
  private val users: concurrent.Map[String, User] = new ConcurrentHashMap[String, User]().asScala

  class User(val id: Long, val pubKey: String)(implicit ec: ExecutionContext) extends Logging {
    private val cachedPools: concurrent.Map[String, Pool] = new ConcurrentHashMap[String, Pool]().asScala

    def sync()(implicit coreEC: LedgerCoreExecutionContext): Future[Seq[SynchronizationResult]] = {
      pools().flatMap { pls =>
        Future.sequence(pls.values.map { p => p.sync()(coreEC) }.toSeq).map (_.flatten)
      }
    }

    def deletePool(name: String): Future[Unit] = {
      dbDao.deletePool(name, id).map { deletedPool =>
        deletedPool.foreach { _ =>
          cachedPools.remove(name).foreach { p =>
            info(LogMsgMaker.newInstance("Pool deleted").append("name", name).append("user_id", id).toString())
            p.unregisterEventReceivers()}
        }
      }
    }

    def addPoolIfNotExit(name: String, configuration: String)(implicit coreEC: LedgerCoreExecutionContext): Future[Pool] = {
      val dto = PoolDto(name, id, configuration)
      dbDao.insertPool(dto).flatMap { poolId =>
        Pool.newCoreInstance(dto).map { coreP =>
          cachedPools.put(name, Pool.newInstance(coreP))
          info(LogMsgMaker.newInstance("Pool created").append("name", name).append("user_id", id).toString())
          cachedPools(name).registerEventReceiver(new NewOperationEventReceiver(poolId, opsCache), coreEC)
          cachedPools(name)
        }
      }.recover {
        case e: WalletPoolAlreadyExistException => {
          warn(LogMsgMaker.newInstance("Pool already exist").append("name", name).append("user_id", id).toString())
          cachedPools(name)
        }
      }
    }

    def pool(name: String): Future[Option[Pool]] = {
      dbDao.getPool(id, name).flatMap {
        case Some(pl) => toCacheAndSetRealTimeObserver(pl).map { _ => cachedPools.get(pl.name)}
        case None => Future.successful(None)
      }
    }

    private def toCacheAndSetRealTimeObserver(p: PoolDto): Future[Unit] = {
      if(cachedPools.contains(p.name))
        Future.successful()
      else
        Pool.newCoreInstance(p).map { coreP =>
          cachedPools.put(p.name, Pool.newInstance(coreP))
          if (DaemonConfiguration.realtimeObserverOn) cachedPools(p.name).startRealTimeObserver()
        }
    }

    def pools(): Future[concurrent.Map[String, Pool]] = dbDao.getPools(id).flatMap { pools =>
      val result = new ConcurrentHashMap[String, Pool]().asScala
      Future.sequence(pools.map { pool =>
        toCacheAndSetRealTimeObserver(pool).map { _ => result.put(pool.name, cachedPools(pool.name)) }
      }).map { _ => result}
    }

    override def toString: String = s"User(id: $id, pubKey: $pubKey)"

  }

}

case class Bulk(offset: Int = 0, bulkSize: Int = 20)
