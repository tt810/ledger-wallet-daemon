package co.ledger.wallet.daemon.database

import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Singleton

import co.ledger.wallet.daemon.exceptions.{AccountNotFoundException, OperationNotFoundException, WalletNotFoundException, WalletPoolNotFoundException}
import co.ledger.wallet.daemon.services.LogMsgMaker
import co.ledger.wallet.daemon.utils
import com.twitter.inject.Logging

import scala.collection.JavaConverters._
import scala.collection.{concurrent, mutable}

@Singleton
class OperationCache extends Logging {

  def insertOperation(id: UUID, poolId: Long, walletName: String, accountIndex: Int, offset: Long, batch: Int, next: Option[UUID], previous: Option[UUID]): AtomicRecord = {
    if (!cache.contains(id)) {
      val newRecord = new AtomicRecord(id, poolId, Option(walletName), Option(accountIndex), batch, new AtomicLong(offset), next, previous)
      cache.put(id, newRecord)
      next match {
        case Some(nextId) => nexts.put(nextId, id)
        case _ => // do nothing
      }
      poolTrees.get(poolId) match {
        case None => {
          val walletTrees = new ConcurrentHashMap[String, concurrent.Map[Int, mutable.Set[UUID]]]().asScala
          val accountTrees = new ConcurrentHashMap[Int, mutable.Set[UUID]]().asScala
          val ops = utils.newConcurrentSet[UUID]
          ops += id
          accountTrees.put(accountIndex, ops)
          walletTrees.put(walletName, accountTrees)
          poolTrees.put(poolId, walletTrees)
        }
        case Some(walletTrees) => walletTrees.get(walletName) match {
          case None => {
            val accountTrees = new ConcurrentHashMap[Int, mutable.Set[UUID]]().asScala
            val ops = utils.newConcurrentSet[UUID]
            ops += id
            accountTrees.put(accountIndex, ops)
            walletTrees.put(walletName, accountTrees)
          }
          case Some(accountTrees) => accountTrees.get(accountIndex) match {
            case None => {
              val ops = utils.newConcurrentSet[UUID]
              ops += id
              accountTrees.put(accountIndex, ops)
            }
            case Some(ops) => ops += id
          }
        }
      }
      newRecord
    } else {
      cache(id)
    }
  }

  def getOperationCandidate(next: UUID): AtomicRecord = {
    cache.get(next) match {
      case None => nexts.get(next) match {
        case Some(current) => cache.get(current) match {
          case Some(record) => new AtomicRecord(next, record.getPoolId(), record.getWalletName(), record.getAccountIndex(), record.getBatch(), new AtomicLong(record.getBatch() + record.getOffset()), Option(UUID.randomUUID()), Option(current))
          case None => throw OperationNotFoundException(current)
        }
        case None => throw OperationNotFoundException(next)
      }
      case Some(op) => op
    }
  }

  def getPreviousOperationRecord(previous: UUID): AtomicRecord = {
    cache.get(previous) match {
      case None => throw OperationNotFoundException(previous)
      case Some(record) => record
    }
  }

  def updateOffset(poolId: Long, walletName: String, accountIndex: Int): Unit = {
    poolTrees.get(poolId) match {
      case Some(walletTrees) => walletTrees.get(walletName) match {
        case Some(accountTrees) => accountTrees.get(accountIndex) match {
          case Some(ops) => ops.foreach { op => cache.get(op) match {
            case Some(record) => {
              val offset = record.incrementOffset()
              info(LogMsgMaker.newInstance("Update offset").append("pool_id", poolId).append("wallet_name", walletName).append("account_index", accountIndex).append("offset", offset).toString())
            }
            case None => throw OperationNotFoundException(op)
          }}
          case None => throw AccountNotFoundException(accountIndex)
        }
        case None => throw WalletNotFoundException(walletName)
      }
      case None => throw WalletPoolNotFoundException(poolId.toString)
    }
  }

  private val cache: concurrent.Map[UUID, AtomicRecord] = new ConcurrentHashMap[UUID, AtomicRecord]().asScala
  private val nexts: concurrent.Map[UUID, UUID] = new ConcurrentHashMap[UUID, UUID]().asScala
  private val poolTrees: concurrent.Map[Long, concurrent.Map[String, concurrent.Map[Int, mutable.Set[UUID]]]] =
    new ConcurrentHashMap[Long, concurrent.Map[String, concurrent.Map[Int, mutable.Set[UUID]]]]().asScala

  class AtomicRecord (private val id: UUID,
                      private val poolId: Long,
                      private val walletName: Option[String],
                      private val accountIndex: Option[Int],
                      private val batch: Int,
                      private val offset: AtomicLong,
                      private val next: Option[UUID],
                      private val previous: Option[UUID]) {

    def incrementOffset(): Long = offset.incrementAndGet()

    def getOffset(): Long = offset.get()

    def getBatch(): Int = batch

    def getPrevious(): Option[UUID] = previous

    def getNext(): Option[UUID] = next

    def getId(): UUID = id

    def getPoolId(): Long = poolId

    def getWalletName(): Option[String] = walletName

    def getAccountIndex(): Option[Int] = accountIndex

  }
}
