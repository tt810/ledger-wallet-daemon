package co.ledger.wallet.daemon.database

import java.util.UUID

import co.ledger.wallet.daemon.exceptions._
import org.junit.Assert._
import co.ledger.wallet.daemon.services.DatabaseService
import djinni.NativeLibLoader
import org.junit.{BeforeClass, Test}
import org.scalatest.junit.AssertionsForJUnit

import scala.concurrent.Await
import scala.concurrent.duration.Duration
import scala.concurrent.ExecutionContext.Implicits.global


class DaemonCacheTest extends AssertionsForJUnit {
  import DaemonCacheTest._

  @Test def verifyGetPoolNotFound(): Unit = {
    try {
      Await.result(cache.getPool(PUB_KEY_1, "pool_not_exist"), Duration.Inf)
      fail()
    } catch {
      case e: WalletPoolNotFoundException => // expected
    }
  }

  @Test def verifyGetPoolsWithNotExistUser(): Unit = {
    try {
      Await.result(cache.getPools(UUID.randomUUID().toString), Duration.Inf)
      fail()
    } catch {
      case e: UserNotFoundException => // expected
    }
  }

  @Test def verifyCreateAndGetPools(): Unit = {
    val pool11 = Await.result(cache.getPool(PUB_KEY_1, "pool_1"), Duration.Inf)
    val pool12 = Await.result(cache.getPool(PUB_KEY_1, "pool_2"), Duration.Inf)
    val pool13 = Await.result(cache.createPool(User(PUB_KEY_1, 0, Option(1L)), "pool_3", "config"), Duration.Inf)
    val pool1s = Await.result(cache.getPools(PUB_KEY_1), Duration.Inf)
    assertEquals(3, pool1s.size)
    assertTrue(pool1s.contains(pool11))
    assertTrue(pool1s.contains(pool12))
    assertTrue(pool1s.contains(pool13))
  }

  @Test def verifyCreateAndDeletePool(): Unit = {
    val poolRandom = Await.result(cache.createPool(User(PUB_KEY_2, 0, Option(2L)),UUID.randomUUID().toString, "config"), Duration.Inf)
    val beforeDeletion = Await.result(cache.getPools(PUB_KEY_2), Duration.Inf)
    assertEquals(3, beforeDeletion.size)
    assertTrue(beforeDeletion.contains(poolRandom))

    val afterDeletion = Await.result(cache.deletePool(User(PUB_KEY_2, 0, Option(2L)), poolRandom.getName).flatMap(_=>cache.getPools(PUB_KEY_2)), Duration.Inf)
    assertFalse(afterDeletion.contains(poolRandom))
  }

  @Test def verifyGetCurrencies(): Unit = {
    val currencies = Await.result(cache.getCurrencies("pool_1"), Duration.Inf)
    assertEquals(1, currencies.size)
    val currency = Await.result(cache.getCurrency("pool_2", "bitcoin"), Duration.Inf)
    assertEquals(currency.getName, currencies(0).getName)
  }

}

object DaemonCacheTest {
  @BeforeClass def initialization(): Unit = {
    NativeLibLoader.loadLibs()
    Await.result(dbDao.insertUser(User(PUB_KEY_1, 0)), Duration.Inf)
    Await.result(dbDao.insertUser(User(PUB_KEY_2, 0)), Duration.Inf)
    Await.result(dbDao.insertPool(Pool("pool_1", 1L, "")), Duration.Inf)
    Await.result(dbDao.insertPool(Pool("pool_2", 1L, "")), Duration.Inf)
    Await.result(dbDao.insertPool(Pool("pool_1", 2L, "")), Duration.Inf)
    Await.result(dbDao.insertPool(Pool("pool_3", 2L, "")), Duration.Inf)
    DefaultDaemonCache.initialize()
  }
  private val cache: DefaultDaemonCache = new DefaultDaemonCache()
  private val dbDao: DatabaseDao = DatabaseService.dbDao
  private val PUB_KEY_1 = UUID.randomUUID().toString
  private val PUB_KEY_2 = UUID.randomUUID().toString
}