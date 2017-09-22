package co.ledger.wallet.daemon.database

import java.util.UUID

import co.ledger.core.WalletPool
import co.ledger.wallet.daemon.exceptions.ResourceNotFoundException
import org.junit.Assert._
import co.ledger.wallet.daemon.services.DatabaseService
import djinni.NativeLibLoader
import org.junit.{BeforeClass, Test}
import org.scalatest.junit.AssertionsForJUnit

import scala.concurrent.Await
import scala.concurrent.duration.Duration
import scala.reflect.ClassTag
import scala.concurrent.ExecutionContext.Implicits.global


class DaemonCacheTest extends AssertionsForJUnit {
  import DaemonCacheTest._

  @Test def verifyGetPoolNotFound(): Unit = {
    try {
      Await.result(cache.getPool(0, "pool_not_exist"), Duration.Inf)
      fail()
    } catch {
      case e: ResourceNotFoundException[ClassTag[WalletPool] @unchecked] => // expected
    }
  }

  @Test def verifyGetPoolsWithNotExistUser(): Unit = {
    try {
      Await.result(cache.getPools(0L), Duration.Inf)
      fail()
    } catch {
      case e: ResourceNotFoundException[ClassTag[User] @unchecked] => // expected
    }
  }

  @Test def verifyCreateAndGetPools(): Unit = {
    val pool11 = Await.result(cache.getPool(1L, "pool_1"), Duration.Inf)
    val pool12 = Await.result(cache.getPool(1L, "pool_2"), Duration.Inf)
    val pool13 = Await.result(cache.createPool(1L, "pool_3", "config"), Duration.Inf)
    val pool1s = Await.result(cache.getPools(1L), Duration.Inf)
    assertEquals(3, pool1s.size)
    assertTrue(pool1s.contains(pool11))
    assertTrue(pool1s.contains(pool12))
    assertTrue(pool1s.contains(pool13))
  }

  @Test def verifyCreateAndDeletePool(): Unit = {
    val poolRandom = Await.result(cache.createPool(2L, UUID.randomUUID().toString, "config"), Duration.Inf)
    val beforeDeletion = Await.result(cache.getPools(2L), Duration.Inf)
    assertEquals(3, beforeDeletion.size)
    assertTrue(beforeDeletion.contains(poolRandom))

    val afterDeletion = Await.result(cache.deletePool(2L, poolRandom.getName).flatMap(_=>cache.getPools(2L)), Duration.Inf)
    assertFalse(afterDeletion.contains(poolRandom))
  }

}

object DaemonCacheTest {
  @BeforeClass def initialization(): Unit = {
    NativeLibLoader.loadLibs()
    Await.result(dbDao.insertUser(User(UUID.randomUUID().toString, 0)), Duration.Inf)
    Await.result(dbDao.insertUser(User(UUID.randomUUID().toString, 0)), Duration.Inf)
    Await.result(dbDao.insertPool(Pool("pool_1", 1L, "")), Duration.Inf)
    Await.result(dbDao.insertPool(Pool("pool_2", 1L, "")), Duration.Inf)
    Await.result(dbDao.insertPool(Pool("pool_1", 2L, "")), Duration.Inf)
    Await.result(dbDao.insertPool(Pool("pool_3", 2L, "")), Duration.Inf)
    DefaultDaemonCache.initialize()
  }
  private val cache: DefaultDaemonCache = new DefaultDaemonCache()
  private val dbDao: DatabaseDao = DatabaseService.dbDao
}