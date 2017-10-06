package co.ledger.wallet.daemon.database

import co.ledger.wallet.daemon.DaemonConfiguration
import co.ledger.wallet.daemon.exceptions.{DaemonDatabaseException, UserAlreadyExistException}
import co.ledger.wallet.daemon.utils.HexUtils
import com.twitter.inject.Logging
import org.scalatest.junit.AssertionsForJUnit
import org.junit.{BeforeClass, Test}
import org.junit.Assert._
import slick.jdbc.JdbcBackend.Database

import scala.concurrent.Await
import scala.concurrent.duration.Duration
import co.ledger.wallet.daemon.async.MDCPropagatingExecutionContext.Implicits.global

class DatabaseDaoTest extends AssertionsForJUnit {
  import DatabaseDaoTest._
  @Test def verifyCreateUser(): Unit = {
    val pubKey = "A3B4A94D8E33308DD08A3A8C937822101E229D85A2C0DFABC236A8C6A82E58076D"
    val expectedUser = UserDTO(pubKey, 0)
    Await.result(dbDao.insertUser(expectedUser), Duration.Inf)
    val actualUser = Await.result(dbDao.getUser(HexUtils.valueOf(pubKey)), Duration.Inf).get
    assertEquals(expectedUser.pubKey, actualUser.pubKey)
    assertEquals(expectedUser.permissions, actualUser.permissions)
    assertNotNull(actualUser.id)
  }

  @Test def verifyCreateUsersWithSamePubKeyFailed(): Unit = {
    val pubKey = "03B4A94D8E33308DD08A3A8C937822101E229D85A2C0DFABC236A8C6A82E58076F"
    val user1 = UserDTO(pubKey, 0)
    val user2 = UserDTO(pubKey, 1)
    Await.result(dbDao.insertUser(user1), Duration.Inf)
    try {
      Await.result(dbDao.insertUser(user2), Duration.Inf)
      fail()
    } catch {
      case e: UserAlreadyExistException => // Excepted
    }
  }

  @Test def verifyCreatePoolWithNonExistUser(): Unit = {
    val expectedPool = PoolDTO("myPool", Long.MaxValue.longValue(), "")
    try {
      Await.result(dbDao.insertPool(expectedPool), Duration.Inf)
      fail()
    } catch {
      case e: DaemonDatabaseException => // user not exist
    }
  }

  @Test def verifyCreatePoolWithExistUser(): Unit = {
    val pubKey = "23B4A94D8E33308DD08A3A8C937822101E229D85A2C0DFABC236A8C6A82E58076D"
    Await.result(dbDao.insertUser(UserDTO(pubKey, 0)), Duration.Inf)
    val insertedUser = Await.result(dbDao.getUser(HexUtils.valueOf(pubKey)), Duration.Inf)
    val expectedPool = PoolDTO("myPool", insertedUser.get.id.get, "")
    Await.result(dbDao.insertPool(expectedPool), Duration.Inf)
    val actualPool =Await.result(dbDao.getPools(insertedUser.get.id.get), Duration.Inf)
    assertEquals(1, actualPool.size)
    assertEquals(expectedPool.name, actualPool(0).name)
    assertEquals(expectedPool.userId, actualPool(0).userId)
  }

  @Test def verifyDeletePoolNotDeletingUserWithNoPool(): Unit = {
    val pubKey = "33B4A94D8E33308DD08A3A8C937822101E229D85A2C0DFABC236A8C6A82E58076D"
    Await.result(dbDao.insertUser(UserDTO(pubKey, 0)), Duration.Inf)
    val insertedUser = Await.result(dbDao.getUser(HexUtils.valueOf(pubKey)), Duration.Inf)
    Await.result(dbDao.insertPool(PoolDTO("myPool", insertedUser.get.id.get, "")), Duration.Inf)
    val existingPools = Await.result(dbDao.getPools(insertedUser.get.id.get), Duration.Inf)
    assertEquals(1, existingPools.size)
    assertEquals("myPool", existingPools(0).name)
    Await.result(dbDao.deletePool("myPool", insertedUser.get.id.get), Duration.Inf)
    val leftoverUser = Await.result(dbDao.getUser(HexUtils.valueOf(pubKey)), Duration.Inf)
    assertFalse("User should not be deleted", leftoverUser.isEmpty)
    assertEquals(0, Await.result(dbDao.getPools(insertedUser.get.id.get), Duration.Inf).size)
  }

  @Test def verifyAccountOperationInsertionAndUpdate(): Unit = {
    val pubKey = "13B2394D8E33308DD08A3A8C937822101E229D85A2C0DFABC236A8C6A82E58076D"
    Await.result(dbDao.insertUser(UserDTO(pubKey, 0)), Duration.Inf)
    val insertedUser = Await.result(dbDao.getUser(HexUtils.valueOf(pubKey)), Duration.Inf)
    Await.result(dbDao.insertPool(PoolDTO("accountPool", insertedUser.get.id.get, "")), Duration.Inf)
    val insertedPool = (Await.result(dbDao.getPool(insertedUser.get.id.get, "accountPool"), Duration.Inf))
    Await.result(dbDao.insertOperation(
      OperationDTO(insertedUser.get.id.get, insertedPool.get.id.get, "myWallet", 1, "opUid0", 0, 20, Option("opUid20"))), Duration.Inf)
    val accountOp = Await.result(dbDao.getFirstAccountOperation(Option("opUid20"), insertedUser.get.id.get, insertedPool.get.id.get, "myWallet", 1), Duration.Inf)
    assertFalse("account operation should be inserted", accountOp.isEmpty)
    Await.result(dbDao.updateOperation(accountOp.get.id.get, "opUid20", 20, 21, Option("opUid41")), Duration.Inf)
    val originalOp = Await.result(dbDao.getFirstAccountOperation(Option("opUid20"), insertedUser.get.id.get, insertedPool.get.id.get, "myWallet", 1), Duration.Inf)
    assertTrue("original operation should no longer exist", originalOp.isEmpty)
    val updatedOp = Await.result(dbDao.getFirstAccountOperation(Option("opUid41"), insertedUser.get.id.get, insertedPool.get.id.get, "myWallet", 1), Duration.Inf)
    assertFalse("operation should be updated", updatedOp.isEmpty)
    assert("opUid20" === updatedOp.get.opUId)
    assert(21 === updatedOp.get.batch)
    assert(20 === updatedOp.get.offset)
    val lastOps = Await.result(dbDao.getFirstAccountOperation(None, insertedUser.get.id.get, insertedPool.get.id.get, "myWallet", 1), Duration.Inf)
    assertTrue("Should not have row in database with nextUid null", lastOps.isEmpty)
  }

}

object DatabaseDaoTest extends Logging {
  @BeforeClass def initialization(): Unit = {
    debug("******************************* before class start")
    Await.result(dbDao.migrate(), Duration.Inf)
    debug("******************************* before class end")
  }
  private val dbDao = new DatabaseDao(Database.forConfig(DaemonConfiguration.dbProfileName))
}