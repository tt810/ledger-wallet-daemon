package co.ledger.wallet.daemon.database

import java.util.UUID

import co.ledger.wallet.daemon.async.MDCPropagatingExecutionContext.Implicits.global
import co.ledger.wallet.daemon.exceptions._
import co.ledger.wallet.daemon.models.{AccountDerivationView, DerivationView}
import com.twitter.util.{Duration => TwitterDuration}
import djinni.NativeLibLoader
import org.junit.Assert._
import org.junit.{BeforeClass, Test}
import org.scalatest.junit.AssertionsForJUnit

import scala.concurrent.Await
import scala.concurrent.duration.Duration

class DaemonCacheTest extends AssertionsForJUnit {
  import DaemonCacheTest._

  @Test def verifyGetPoolNotFound(): Unit = {
    try {
      Await.result(cache.getWalletPool(PUB_KEY_1, "pool_not_exist"), Duration.Inf)
      fail()
    } catch {
      case e: WalletPoolNotFoundException => // expected
    }
  }

  @Test def verifyGetPoolsWithNotExistUser(): Unit = {
    try {
      Await.result(cache.getWalletPools(UUID.randomUUID().toString), Duration.Inf)
      fail()
    } catch {
      case e: UserNotFoundException => // expected
    }
  }

  @Test def verifyCreateAndGetPools(): Unit = {
    val pool11 = Await.result(cache.getWalletPool(PUB_KEY_1, "pool_1"), Duration.Inf)
    val pool12 = Await.result(cache.getWalletPool(PUB_KEY_1, "pool_2"), Duration.Inf)
    val pool13 = Await.result(cache.createWalletPool(UserDto(PUB_KEY_1, 0, Option(1L)), "pool_3", "config"), Duration.Inf)
    val pool1s = Await.result(cache.getWalletPools(PUB_KEY_1), Duration.Inf)
    assertEquals(3, pool1s.size)
    assertTrue(pool1s.contains(pool11))
    assertTrue(pool1s.contains(pool12))
    assertTrue(pool1s.contains(pool13))
  }

  @Test def verifyCreateAndDeletePool(): Unit = {
    val poolRandom = Await.result(cache.createWalletPool(UserDto(PUB_KEY_2, 0, Option(2L)),UUID.randomUUID().toString, "config"), Duration.Inf)
    val beforeDeletion = Await.result(cache.getWalletPools(PUB_KEY_2), Duration.Inf)
    assertEquals(3, beforeDeletion.size)
    assertTrue(beforeDeletion.contains(poolRandom))

    val afterDeletion = Await.result(cache.deleteWalletPool(UserDto(PUB_KEY_2, 0, Option(2L)), poolRandom.name).flatMap(_=>cache.getWalletPools(PUB_KEY_2)), Duration.Inf)
    assertFalse(afterDeletion.contains(poolRandom))
  }

  @Test def verifyGetCurrencies(): Unit = {
    val currencies = Await.result(cache.getCurrencies("pool_1"), Duration.Inf)
    assertEquals(1, currencies.size)
    val currency = Await.result(cache.getCurrency("bitcoin", "pool_2"), Duration.Inf)
    assertEquals(currency.name, currencies(0).name)
  }

  @Test def verifyGetAccountOperations(): Unit = {
    val firstOp = Await.result(DefaultDaemonCache.dbDao.getOperation(1L), Duration.Inf)
    println(firstOp)
    val user1 = Await.result(cache.getUserDirectlyFromDB(PUB_KEY_3), Duration.Inf)
    val pool1 = Await.result(DefaultDaemonCache.dbDao.getPool(user1.get.id.get, POOL_NAME), Duration.Inf)
    Await.result(cache.getAccountOperations(user1.get, 0, pool1.get.name, WALLET_NAME, firstOp.get.offset.toInt + 100, 0), Duration.Inf)
    val withTxs = Await.result(cache.getAccountOperations(user1.get, 0, pool1.get.name, WALLET_NAME, 1, 1), Duration.Inf)
    withTxs.operations.foreach(op => assertNotNull(op.transaction))
    val ops = Await.result(cache.getAccountOperations(user1.get, 0, pool1.get.name, WALLET_NAME, 2, 0), Duration.Inf)
    assert(ops.previous.isEmpty)
    assert(2 === ops.operations.size)
    assert(ops.previous.isEmpty)
    ops.operations.foreach(op => assertNull(op.transaction))
    val nextOps = Await.result(cache.getNextBatchAccountOperations(user1.get, 0, pool1.get.name, WALLET_NAME, ops.next.get, 0), Duration.Inf)

    val previousOfNextOps = Await.result(cache.getPreviousBatchAccountOperations(user1.get, 0, pool1.get.name, WALLET_NAME, nextOps.previous.get, 0), Duration.Inf)
    assert(ops === previousOfNextOps)

    val nextnextOps = Await.result(cache.getNextBatchAccountOperations(user1.get, 0, pool1.get.name, WALLET_NAME, nextOps.next.get, 0), Duration.Inf)

    val previousOfNextNextOps = Await.result(cache.getPreviousBatchAccountOperations(user1.get, 0, pool1.get.name, WALLET_NAME, nextnextOps.previous.get, 0), Duration.Inf)
    assert(nextOps === previousOfNextNextOps)

    val theVeryFirstOps = Await.result(cache.getPreviousBatchAccountOperations(user1.get, 0, pool1.get.name, WALLET_NAME, previousOfNextNextOps.previous.get, 0), Duration.Inf)
    assert(ops === theVeryFirstOps)

    val maxi = Await.result(cache.getAccountOperations(user1.get, 0, pool1.get.name, WALLET_NAME, Int.MaxValue, 0), Duration.Inf)
    assert(maxi.operations.size < Int.MaxValue)
    assert(maxi.previous.isEmpty)
    assert(maxi.next.isEmpty)
  }

}

object DaemonCacheTest {
  @BeforeClass def initialization(): Unit = {
    NativeLibLoader.loadLibs()
    Await.result(DefaultDaemonCache.migrateDatabase(), Duration.Inf)
    Await.result(cache.createUser(UserDto(PUB_KEY_1, 0, None)), Duration.Inf)
    Await.result(cache.createUser(UserDto(PUB_KEY_2, 0, None)), Duration.Inf)
    Await.result(cache.createUser(UserDto(PUB_KEY_3, 0, None)), Duration.Inf)
    val user1 = Await.result(cache.getUserDirectlyFromDB(PUB_KEY_1), Duration.Inf)
    val user2 = Await.result(cache.getUserDirectlyFromDB(PUB_KEY_2), Duration.Inf)
    val user3 = Await.result(cache.getUserDirectlyFromDB(PUB_KEY_3), Duration.Inf)
    Await.result(cache.createWalletPool(user1.get, "pool_1", ""), Duration.Inf)
    Await.result(cache.createWalletPool(user1.get, "pool_2", ""), Duration.Inf)
    Await.result(cache.createWalletPool(user2.get, "pool_1", ""), Duration.Inf)
    Await.result(cache.createWalletPool(user2.get, "pool_3", ""), Duration.Inf)
    Await.result(cache.createWalletPool(user3.get, POOL_NAME, ""), Duration.Inf)
    Await.result(cache.createWallet(WALLET_NAME, "bitcoin", POOL_NAME, user3.get), Duration.Inf)
    Await.result(cache.createAccount(
      AccountDerivationView(0, List(
        DerivationView("44'/0'/0'", "main", Option("0437bc83a377ea025e53eafcd18f299268d1cecae89b4f15401926a0f8b006c0f7ee1b995047b3e15959c5d10dd1563e22a2e6e4be9572aa7078e32f317677a901"), Option("d1bb833ecd3beed6ec5f6aa79d3a424d53f5b99147b21dbc00456b05bc978a71")),
        DerivationView("44'/0'", "main", Option("0437bc83a377ea025e53eafcd18f299268d1cecae89b4f15401926a0f8b006c0f7ee1b995047b3e15959c5d10dd1563e22a2e6e4be9572aa7078e32f317677a901"), Option("d1bb833ecd3beed6ec5f6aa79d3a424d53f5b99147b21dbc00456b05bc978a71")))),
        user3.get,
        POOL_NAME,
        WALLET_NAME), Duration.Inf)
    Await.result(cache.getAccountOperations(user3.get, 0, POOL_NAME, WALLET_NAME, 1, 1), Duration.Inf)
    Await.result(cache.syncOperations(), Duration.Inf)

    DefaultDaemonCache.initialize()
  }

  private val cache: DefaultDaemonCache = new DefaultDaemonCache()
  private val PUB_KEY_1 = UUID.randomUUID().toString
  private val PUB_KEY_2 = UUID.randomUUID().toString
  private val PUB_KEY_3 = UUID.randomUUID().toString
  private val WALLET_NAME = UUID.randomUUID().toString
  private val POOL_NAME = UUID.randomUUID().toString
}