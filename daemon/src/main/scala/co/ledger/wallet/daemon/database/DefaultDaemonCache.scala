package co.ledger.wallet.daemon.database

import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Singleton

import co.ledger.wallet.daemon.async.MDCPropagatingExecutionContext
import co.ledger.wallet.daemon.configurations.DaemonConfiguration
import co.ledger.wallet.daemon.exceptions.{AccountNotFoundException, UserNotFoundException, WalletPoolAlreadyExistException, WalletPoolNotFoundException}
import co.ledger.wallet.daemon.models.Account.{Account, Derivation}
import co.ledger.wallet.daemon.models._
import co.ledger.wallet.daemon.schedulers.observers.{NewOperationEventReceiver, SynchronizationResult}
import co.ledger.wallet.daemon.services.LogMsgMaker
import co.ledger.wallet.daemon.exceptions
import com.twitter.inject.Logging
import slick.jdbc.JdbcBackend.Database

import scala.collection.JavaConverters._
import scala.collection._
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

@Singleton
class DefaultDaemonCache() extends DaemonCache with Logging {
  implicit val ec: ExecutionContext = MDCPropagatingExecutionContext.Implicits.global
  import DefaultDaemonCache._


  def dbMigration: Future[Unit] = {
    dbDao.migrate()
  }

  def syncOperations(): Future[Seq[SynchronizationResult]] = {
    getUsers.flatMap { usrs =>
      Future.sequence(usrs.map { user => user.sync()}).map (_.flatten)
    }
  }

  def getAccount(accountIndex: Int, pubKey: String, poolName: String, walletName: String): Future[Option[Account]] = {
    getHardWallet(pubKey, poolName, walletName).flatMap { wallet => wallet.account(accountIndex) }
  }

  def getAccounts(pubKey: String, poolName: String, walletName: String): Future[Seq[Account]] = {
    getHardWallet(pubKey, poolName, walletName).flatMap { wallet => wallet.accounts() }
  }

  def createAccount(accountDerivations: AccountDerivationView, user: User, poolName: String, walletName: String): Future[Account] = {
    getHardWallet(user.pubKey, poolName, walletName).flatMap { w =>
      w.addAccountIfNotExit(accountDerivations).andThen {
        case Success(a) => a.sync(poolName)
        case Failure(t: Throwable) => t
      }
    }
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
      user.addPoolIfNotExit(poolName, configuration)
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

  def createUser(pubKey: String, permissions: Long): Future[Long] = {
    val user = UserDto(pubKey, permissions)
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
    getHardPool(user, poolName).flatMap { pool =>
      getHardAccount(user.pubKey, poolName, walletName, accountIndex).flatMap { pair =>
        val candidate = opsCache.getOperationCandidate(next)
        pair._2.operations(candidate.offset(), candidate.batch, fullOp).flatMap { ops =>
          val realBatch = if (ops.size < candidate.batch) ops.size else candidate.batch
          val next = if (realBatch < candidate.batch) None else candidate.next
          val previous = candidate.previous
          val operationRecord = opsCache.insertOperation(candidate.id, pool.id, walletName, accountIndex, candidate.offset(), candidate.batch, next, previous)
          Future.sequence(ops.map { op => op.operationView  })
            .map { os => PackedOperationsView(operationRecord.previous, operationRecord.next, os)}
        }
      }
    }
  }

  def getAccountOperations(user: User, accountIndex: Int, poolName: String, walletName: String, batch: Int, fullOp: Int): Future[PackedOperationsView] = {
    getHardPool(user, poolName).flatMap { pool =>
      getHardAccount(user.pubKey, poolName, walletName, accountIndex).flatMap { pair =>
        val offset = 0
        pair._2.operations(offset, batch, fullOp).flatMap { ops =>
          val realBatch = if (ops.size < batch) ops.size else batch
          val next = if (realBatch < batch) None else Option(UUID.randomUUID())
          val previous = None
          val operationRecord = opsCache.insertOperation(UUID.randomUUID(), pool.id, walletName, accountIndex, offset, batch, next, previous)
          Future.sequence(ops.map { op => op.operationView })
            .map { os => PackedOperationsView(operationRecord.previous, operationRecord.next, os) }
        }
      }
    }
  }

  def getAccountOperation(user: User, uid: String, accountIndex: Int, poolName: String, walletName: String, fullOp: Int): Future[Option[Operation]] = {
    getHardAccount(user.pubKey, poolName, walletName, accountIndex).flatMap { pair => pair._2.operation(uid, fullOp) }
  }

  private def getHardAccount(pubKey: String, poolName: String, walletName: String, accountIndex: Int): Future[(Wallet, Account)] = {
    getHardWallet(pubKey, poolName, walletName).flatMap { wallet =>
      wallet.account(accountIndex).map {
        case Some(a) => (wallet, a)
        case None => throw AccountNotFoundException(accountIndex)
      }
    }
  }

  private def getHardPool(user: User, poolName: String): Future[Pool] = {
    user.pool(poolName).map {
      case None => throw WalletPoolNotFoundException(poolName)
      case Some(p) => p
    }
  }

}

object DefaultDaemonCache extends Logging {

  def newUser(user: UserDto): User = {
    assert(user.id.isDefined, "User id must exist")
    new User(user.id.get, user.pubKey)
  }

  private[database] val dbDao             =   new DatabaseDao(Database.forConfig(DaemonConfiguration.dbProfileName))
  private[database] val opsCache: OperationCache = new OperationCache()
  private val users: concurrent.Map[String, User] = new ConcurrentHashMap[String, User]().asScala

  class User(val id: Long, val pubKey: String) extends Logging with GenCache {
    implicit val ec: ExecutionContext = MDCPropagatingExecutionContext.Implicits.global
    private[this] val cachedPools: Cache[String, Pool] = newCache(initialCapacity = 50)
    private[this] val self = this

    def sync(): Future[Seq[SynchronizationResult]] = {
      pools().flatMap { pls =>
        Future.sequence(pls.values.map { p => p.sync() }.toSeq).map (_.flatten)
      }
    }

    /**
      * Delete pool will:
      *  1. remove the pool from daemon database
      *  2. unsubscribe event receivers to core library, see details on method `clear` from `Pool`
      *  3. remove the operations were done on this pool, which includes all underlying wallets and accounts
      *  4. remove the pool from cache.
      *
      * @param name the name of wallet pool needs to be deleted.
      * @return a Future of Unit.
      */
    def deletePool(name: String): Future[Unit] = {
      dbDao.deletePool(name, id).flatMap { deletedPool =>
        if (deletedPool.isDefined) opsCache.deleteOperations(deletedPool.get.id.get)
        clearCache(name).map { _ =>
          info(LogMsgMaker.newInstance("Pool deleted").append("name", name).append("user_id", id).toString())
        }
      }
    }

    private def clearCache(poolName: String): Future[Unit] = cachedPools.remove(poolName) match {
      case Some(p) => p.clear
      case None => Future.successful()
    }

    def addPoolIfNotExit(name: String, configuration: String): Future[Pool] = {
      val dto = PoolDto(name, id, configuration)
      dbDao.insertPool(dto).flatMap { id =>
        toCacheAndStartListen(dto, id).map { pool =>
          info(LogMsgMaker.newInstance("Pool created").append("name", name).append("user_id", id).toString())
          pool
        }
      }.recover {
        case e: WalletPoolAlreadyExistException => {
          warn(LogMsgMaker.newInstance("Pool already exist").append("name", name).append("user_id", id).toString())
          cachedPools(name)
        }
      }
    }

    /**
      * Getter for individual pool with specified name. This method will perform a daemon database search in order
      * to get the most up to date information. If specified pool doesn't exist in database but in cache. The cached
      * pool will be cleared. See `clear` method from Pool for detailed actions.
      *
      * @param name the name of wallet pool.
      * @return a Future of `co.ledger.wallet.daemon.models.Pool` instance Option.
      */
    def pool(name: String): Future[Option[Pool]] = {
      dbDao.getPool(id, name).flatMap {
        case Some(p) => toCacheAndStartListen(p, p.id.get).map(Option(_))
        case None => clearCache(name).map { _ => None }
      }
    }

    private def toCacheAndStartListen(p: PoolDto, id: Long): Future[Pool] = {
      cachedPools.get(p.name) match {
        case Some(pool) => Future.successful(pool)
        case None => Pool.newCoreInstance(p).flatMap { coreP =>
          cachedPools.put(p.name, Pool.newInstance(coreP, id))
          debug(s"Add ${cachedPools(p.name)} to $self cache")
          cachedPools(p.name).registerEventReceiver(new NewOperationEventReceiver(id, opsCache))
          cachedPools(p.name).startCacheAndRealTimeObserver().map { _ => cachedPools(p.name)}
        }
      }
    }

    /**
      * Obtain available pools of this user. The method performs database call(s), adds the missing
      * pools to cache.
      *
      * @return the resulting pool map with pool name as key. The result map may contain less pools
      *         than the cached pools.
      */
    def pools(): Future[concurrent.Map[String, Pool]] = dbDao.getPools(id).flatMap { pools =>
      val result = new ConcurrentHashMap[String, Pool]().asScala
      Future.sequence(pools.map { pool =>
        toCacheAndStartListen(pool, pool.id.get).map { p => result.put(pool.name, p) }
      }).map { _ => result}
    }

    override def toString: String = s"User(id: $id, pubKey: $pubKey)"

  }

}

case class Bulk(offset: Int = 0, bulkSize: Int = 20)
