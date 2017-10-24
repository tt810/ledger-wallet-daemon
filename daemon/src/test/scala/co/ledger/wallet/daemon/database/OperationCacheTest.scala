package co.ledger.wallet.daemon.database

import java.util.UUID

import org.junit.Test
import org.scalatest.junit.AssertionsForJUnit

class OperationCacheTest extends AssertionsForJUnit {
  private val operationCache: OperationCache = new OperationCache()
  import operationCache._

  @Test def verifyAccountOperationNextUnique(): Unit = {
    val previous = Option(UUID.randomUUID())
    val next = Option(UUID.randomUUID())
    val id = UUID.randomUUID()
    val record = operationCache.insertOperation(id, 1L, "myWallet", 1, 0, 20, next, previous)
    assert(record === operationCache.getPreviousOperationRecord(id))
    val original = operationCache.insertOperation(id, 1L, "myWallet", 1, 2, 20, next, previous)
    assert(record === original)
  }

  @Test def verifyGetOperationCandidates(): Unit = {
    val previous = Option(UUID.randomUUID())
    val next = Option(UUID.randomUUID())
    val id = UUID.randomUUID()
    val record = operationCache.insertOperation(id, 1L, "myWallet", 1, 0, 20, next, previous)
    val nextRecord = operationCache.getOperationCandidate(next.get)
    val insertedNext = insertRecord(nextRecord)
    assert(insertedNext != nextRecord)
    assert(insertedNext.getId() === nextRecord.getId())
    assert(nextRecord.getOffset() === insertedNext.getOffset())
    assert(nextRecord.getNext() === insertedNext.getNext())
    val nextNextRecord = operationCache.getOperationCandidate(nextRecord.getNext().get)
    assert(nextNextRecord.getId() === nextRecord.getNext().get)
  }

  @Test def verifyGetPreviousOperations(): Unit = {
    val next = Option(UUID.randomUUID())
    val id = UUID.randomUUID()
    val record = operationCache.insertOperation(id, 1L, "myWallet", 1, 0, 20, next, None)
    val nextRecord = insertRecord(operationCache.getOperationCandidate(next.get))
    val previousOfNext = operationCache.getPreviousOperationRecord(nextRecord.getPrevious().get)
    assert(previousOfNext === record)
  }

  private def insertRecord(record: AtomicRecord) = {
    operationCache.insertOperation(
      record.getId(),
      record.getPoolId(),
      record.getWalletName().get,
      record.getAccountIndex().get,
      record.getOffset(),
      record.getBatch(),
      record.getNext(),
      record.getPrevious())
  }
}
