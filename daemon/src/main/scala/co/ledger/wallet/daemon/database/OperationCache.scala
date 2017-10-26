package co.ledger.wallet.daemon.database

import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Singleton

import co.ledger.wallet.daemon.exceptions.OperationNotFoundException
import co.ledger.wallet.daemon.services.LogMsgMaker
import co.ledger.wallet.daemon.utils
import com.twitter.inject.Logging

import scala.collection.JavaConverters._
import scala.collection.{concurrent, mutable}

@Singleton
class OperationCache extends Logging {

  /**
    * Create and insert an operation record.
    *
    * @param id
    * @param poolId
    * @param walletName
    * @param accountIndex
    * @param offset
    * @param batch
    * @param next
    * @param previous
    * @return
    */
  def insertOperation(id: UUID, poolId: Long, walletName: String, accountIndex: Int, offset: Long, batch: Int, next: Option[UUID], previous: Option[UUID]): AtomicRecord = {
    if (cache.contains(id)) cache(id)
    else {
      val newRecord = new AtomicRecord(id, poolId, Option(walletName), Option(accountIndex), batch, new AtomicLong(offset), next, previous)
      cache.put(id, newRecord)
      next.map { nexts.put(_, id)}
      poolTrees.get(poolId) match {
        case Some(poolTree) => poolTree.insertOperation(walletName, accountIndex, newRecord.id)
        case None => poolTrees.put(poolId, newPoolTreeInstance(poolId, walletName, accountIndex, newRecord.id))
      }
      newRecord
    }
  }

  /**
    * Getter for obtaining existing operation record as candidate with specified id or if not exist create a new record as candidate.
    *
    * The logic matches:
    *
    * None        |R1          |R2           |R3          |
    *             |            |R1.next      |R2.next     |R3.next
    * R1.previous |R2.previous |R3.previous  |            |
    *
    * If (id == R3) then R3 will be returned as candidate
    * If (id == R3.next) then a new record as the next of R3 will be returned as candidate
    *
    * @param id
    * @return
    */
  def getOperationCandidate(id: UUID): AtomicRecord = {
    cache.get(id) match {
      case None => nexts.get(id) match {
        case Some(current) => cache.get(current) match {
          case Some(record) => new AtomicRecord(id, record.poolId, record.walletName, record.accountIndex, record.batch, new AtomicLong(record.batch + record.offset()), Option(UUID.randomUUID()), Option(current))
          case None => throw OperationNotFoundException(current)
        }
        case None => throw OperationNotFoundException(id)
      }
      case Some(op) => op
    }
  }

  /**
    * Getter for obtaining record with specified id or if not exist the previous record of this id.
    *
    * The logic matches:
    *
    * None        |R1          |R2           |R3          |
    *             |            |R1.next      |R2.next     |R3.next
    * R1.previous |R2.previous |R3.previous  |            |
    *
    * If (id == R2.id) then R2 will be returned
    * If (id == R3.next) then R3 will be returned
    *
    * @param id
    * @return
    */
  def getPreviousOperationRecord(id: UUID): AtomicRecord = {
    cache.get(id) match {
      case None => nexts.get(id) match {
        case Some(id) => cache.get(id) match {
          case None => throw OperationNotFoundException(id)
          case Some(record) => record
        }
        case None => throw OperationNotFoundException(id)
      }
      case Some(record) => record
    }
  }

  /**
    * Update offset of existing records with specified identifiers.
    *
    * @param poolId
    * @param walletName
    * @param accountIndex
    */
  def updateOffset(poolId: Long, walletName: String, accountIndex: Int): Unit = {
    if (poolTrees.contains(poolId))
      poolTrees(poolId).operations(walletName, accountIndex).map { op =>
        val lastOffset = cache(op).incrementOffset()
        debug(LogMsgMaker.newInstance("Update offset").append("to", lastOffset).append("pool", poolId).append("wallet", walletName).append("account", accountIndex).toString())
      }
  }

  /**
    * Delete operations of specified pool id.
    *
    * @param pool the pool id.
    */
  def deleteOperations(pool: Long): Unit = {
    poolTrees.remove(pool).foreach { poolTree =>
      poolTree.operations.foreach { op =>
        // remove operation record from cache and nexts
        cache.remove(op).map { record => record.next.map { nextUUID => nexts.remove(nextUUID) } }
      }
    }
  }

  private def newPoolTreeInstance(pool: Long, wallet: String, account: Int, operation: UUID): PoolTree = {
    val wallets = new ConcurrentHashMap[String, WalletTree]().asScala
    wallets.put(wallet, newWalletTreeInstance(wallet, account, operation))
    new PoolTree(pool, wallets)
  }

  private val cache: concurrent.Map[UUID, AtomicRecord] = new ConcurrentHashMap[UUID, AtomicRecord]().asScala
  private val nexts: concurrent.Map[UUID, UUID] = new ConcurrentHashMap[UUID, UUID]().asScala
  private val poolTrees: concurrent.Map[Long, PoolTree] = new ConcurrentHashMap[Long, PoolTree]().asScala

  class PoolTree(val poolId: Long, val wallets: concurrent.Map[String, WalletTree]) {

    def insertOperation(wallet: String, account: Int, operation: UUID): Unit = wallets.get(wallet) match {
      case Some(tree) => tree.insertOperation(account, operation)
      case None => wallets.put(wallet, newWalletTreeInstance(wallet, account, operation))
    }

    def operations(wallet: String, account: Int): Set[UUID] = if (wallets.contains(wallet)) wallets(wallet).operations(account) else Set.empty[UUID]

    def operations: Set[UUID] = wallets.values.flatMap { wallet => wallet.operations }.toSet
  }

  def newWalletTreeInstance(walletName: String, accountIndex: Int, operation: UUID): WalletTree = {
    val accounts = new ConcurrentHashMap[Int, AccountTree]().asScala
    accounts.put(accountIndex, newAccountTreeInstance(accountIndex, operation))
    new WalletTree(walletName, accounts)
  }

  class WalletTree(val walletName: String, val accounts: concurrent.Map[Int, AccountTree]) {

    def insertOperation(account: Int, operation: UUID): Unit = accounts.get(account) match {
      case Some(tree) => tree.insertOperation(operation)
      case None => accounts.put(account, newAccountTreeInstance(account, operation))
    }

    def operations(account: Int): Set[UUID] = if (accounts.contains(account)) accounts(account).operations.toSet else Set.empty[UUID]

    def operations: Set[UUID] = accounts.values.flatMap { account => account.operations }.toSet
  }

  def newAccountTreeInstance(index: Int, operation: UUID): AccountTree = {
    new AccountTree(index, utils.newConcurrentSet[UUID] += operation)
  }

  class AccountTree(val index: Int, val operations:  mutable.Set[UUID]) {

    def containsOperation(operationId: UUID): Boolean = operations.contains(operationId)

    def insertOperation(operationId: UUID): operations.type = operations += operationId
  }

  class AtomicRecord (val id: UUID,
                      val poolId: Long,
                      val walletName: Option[String],
                      val accountIndex: Option[Int],
                      val batch: Int,
                      private val ofst: AtomicLong,
                      val next: Option[UUID],
                      val previous: Option[UUID]) {

    def incrementOffset(): Long = ofst.incrementAndGet()

    def offset(): Long = ofst.get()
  }
}
