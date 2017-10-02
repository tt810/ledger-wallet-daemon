package co.ledger.wallet.daemon.database

import java.util.UUID

import co.ledger.wallet.daemon.exceptions._
import org.junit.Assert._
import djinni.NativeLibLoader
import org.junit.{BeforeClass, Test}
import org.scalatest.junit.AssertionsForJUnit

import scala.concurrent.Await
import scala.concurrent.duration.Duration
import co.ledger.wallet.daemon.async.MDCPropagatingExecutionContext.Implicits.global


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
    val pool13 = Await.result(cache.createWalletPool(User(PUB_KEY_1, 0, Option(1L)), "pool_3", "config"), Duration.Inf)
    val pool1s = Await.result(cache.getWalletPools(PUB_KEY_1), Duration.Inf)
    assertEquals(3, pool1s.size)
    assertTrue(pool1s.contains(pool11))
    assertTrue(pool1s.contains(pool12))
    assertTrue(pool1s.contains(pool13))
  }

  @Test def verifyCreateAndDeletePool(): Unit = {
    val poolRandom = Await.result(cache.createWalletPool(User(PUB_KEY_2, 0, Option(2L)),UUID.randomUUID().toString, "config"), Duration.Inf)
    val beforeDeletion = Await.result(cache.getWalletPools(PUB_KEY_2), Duration.Inf)
    assertEquals(3, beforeDeletion.size)
    assertTrue(beforeDeletion.contains(poolRandom))

    val afterDeletion = Await.result(cache.deleteWalletPool(User(PUB_KEY_2, 0, Option(2L)), poolRandom.name).flatMap(_=>cache.getWalletPools(PUB_KEY_2)), Duration.Inf)
    assertFalse(afterDeletion.contains(poolRandom))
  }

  @Test def verifyGetCurrencies(): Unit = {
    val currencies = Await.result(cache.getCurrencies("pool_1"), Duration.Inf)
    assertEquals(1, currencies.size)
    val currency = Await.result(cache.getCurrency("bitcoin", "pool_2"), Duration.Inf)
    assertEquals(currency.name, currencies(0).name)
  }

}

object DaemonCacheTest {
  @BeforeClass def initialization(): Unit = {
    NativeLibLoader.loadLibs()
    Await.result(DefaultDaemonCache.migrateDatabase(), Duration.Inf)
    val user1 = User(PUB_KEY_1, 0, Option(1L))
    val user2 = User(PUB_KEY_2, 0, Option(2L))
    Await.result(cache.createUser(user1), Duration.Inf)
    Await.result(cache.createUser(user2), Duration.Inf)
    Await.result(cache.createWalletPool(user1, "pool_1", ""), Duration.Inf)
    Await.result(cache.createWalletPool(user1, "pool_2", ""), Duration.Inf)
    Await.result(cache.createWalletPool(user2, "pool_1", ""), Duration.Inf)
    Await.result(cache.createWalletPool(user2, "pool_3", ""), Duration.Inf)
    DefaultDaemonCache.initialize()
  }
  private val cache: DefaultDaemonCache = new DefaultDaemonCache()
  private val PUB_KEY_1 = UUID.randomUUID().toString
  private val PUB_KEY_2 = UUID.randomUUID().toString
}