package co.ledger.wallet.daemon.database

import java.util.UUID

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
    val expectedUser = UserDto(pubKey, 0)
    Await.result(dbDao.insertUser(expectedUser), Duration.Inf)
    val actualUser = Await.result(dbDao.getUser(HexUtils.valueOf(pubKey)), Duration.Inf).get
    assertEquals(expectedUser.pubKey, actualUser.pubKey)
    assertEquals(expectedUser.permissions, actualUser.permissions)
    assertNotNull(actualUser.id)
  }

  @Test def verifyCreateUsersWithSamePubKeyFailed(): Unit = {
    val pubKey = "03B4A94D8E33308DD08A3A8C937822101E229D85A2C0DFABC236A8C6A82E58076F"
    val user1 = UserDto(pubKey, 0)
    val user2 = UserDto(pubKey, 1)
    Await.result(dbDao.insertUser(user1), Duration.Inf)
    try {
      Await.result(dbDao.insertUser(user2), Duration.Inf)
      fail()
    } catch {
      case e: UserAlreadyExistException => // Excepted
    }
  }

  @Test def verifyCreatePoolWithNonExistUser(): Unit = {
    val expectedPool = PoolDto("myPool", Long.MaxValue.longValue(), "")
    try {
      Await.result(dbDao.insertPool(expectedPool), Duration.Inf)
      fail()
    } catch {
      case e: DaemonDatabaseException => // user not exist
    }
  }

  @Test def verifyCreatePoolWithExistUser(): Unit = {
    val pubKey = "23B4A94D8E33308DD08A3A8C937822101E229D85A2C0DFABC236A8C6A82E58076D"
    val id = Await.result(dbDao.insertUser(UserDto(pubKey, 0)), Duration.Inf)
    val insertedUser = Await.result(dbDao.getUser(HexUtils.valueOf(pubKey)), Duration.Inf)
    assert(id === insertedUser.get.id.get)
    val expectedPool = PoolDto("myPool", insertedUser.get.id.get, "")
    Await.result(dbDao.insertPool(expectedPool), Duration.Inf)
    val actualPool =Await.result(dbDao.getPools(insertedUser.get.id.get), Duration.Inf)
    assertEquals(1, actualPool.size)
    assertEquals(expectedPool.name, actualPool(0).name)
    assertEquals(expectedPool.userId, actualPool(0).userId)
  }

  @Test def verifyReturnInsertedUserIds(): Unit = {
    val pubKey1 = "232AA94D8E33308DD08A3A8C937822101E229D85A2C0DFABC236A8C6A82E58076D"
    val id1 = Await.result(dbDao.insertUser(UserDto(pubKey1, 0)), Duration.Inf)
    val insertedUser1 = Await.result(dbDao.getUser(HexUtils.valueOf(pubKey1)), Duration.Inf)
    assert(id1 === insertedUser1.get.id.get)
    val pubKey2 = "232AA94D8E33308DA08A3A8CA37D22101E212D85A2C0DFABC236A8C6A82E580D6A"
    val id2 = Await.result(dbDao.insertUser(UserDto(pubKey2, 0)), Duration.Inf)
    val insertedUser2 = Await.result(dbDao.getUser(HexUtils.valueOf(pubKey2)), Duration.Inf)
    assert(id2 === insertedUser2.get.id.get)
    val pubKey3 = "922AA9DD8ED3308DD08A3A8CA378A2101E229D85A111DEEBC236A8C6A82E580D6A"
    val id3 = Await.result(dbDao.insertUser(UserDto(pubKey3, 0)), Duration.Inf)
    val insertedUser3 = Await.result(dbDao.getUser(HexUtils.valueOf(pubKey3)), Duration.Inf)
    assert(id3 === insertedUser3.get.id.get)
  }

  @Test def verifyDeletePoolNotDeletingUserWithNoPool(): Unit = {
    val pubKey = "33B4A94D8E33308DD08A3A8C937822101E229D85A2C0DFABC236A8C6A82E58076D"
    Await.result(dbDao.insertUser(UserDto(pubKey, 0)), Duration.Inf)
    val insertedUser = Await.result(dbDao.getUser(HexUtils.valueOf(pubKey)), Duration.Inf)
    val poolId = Await.result(dbDao.insertPool(PoolDto("myPool", insertedUser.get.id.get, "")), Duration.Inf)
    val existingPools = Await.result(dbDao.getPools(insertedUser.get.id.get), Duration.Inf)
    assertEquals(1, existingPools.size)
    assertEquals(poolId, existingPools(0).id.get)
    assertEquals("myPool", existingPools(0).name)
    val deletedPool = Await.result(dbDao.deletePool("myPool", insertedUser.get.id.get), Duration.Inf)
    assertEquals(deletedPool, Option(existingPools(0)))
    val leftoverUser = Await.result(dbDao.getUser(HexUtils.valueOf(pubKey)), Duration.Inf)
    assertFalse("User should not be deleted", leftoverUser.isEmpty)
    assertEquals(0, Await.result(dbDao.getPools(insertedUser.get.id.get), Duration.Inf).size)
  }

  @Test def verifyAccountOperationPreviousUnique(): Unit = {
    val pubKey = "03A1994D8E33308DD08A3A8C937822101E229D85A2C0DFABC236A8C6A82E58076D"
    Await.result(dbDao.insertUser(UserDto(pubKey, 0)), Duration.Inf)
    val insertedUser = Await.result(dbDao.getUser(HexUtils.valueOf(pubKey)), Duration.Inf)
    Await.result(dbDao.insertPool(PoolDto("accountPool", insertedUser.get.id.get, "")), Duration.Inf)
    val insertedPool = (Await.result(dbDao.getPool(insertedUser.get.id.get, "accountPool"), Duration.Inf))
    val previous = Option(UUID.randomUUID())
    val next = Option(UUID.randomUUID())
    Await.result(dbDao.insertOperation(
      OperationDto(insertedUser.get.id.get, insertedPool.get.id.get, Option("myWallet"), Option(1), previous, 0, 20, next)), Duration.Inf)
    try {
      Await.result(dbDao.insertOperation(
        OperationDto(insertedUser.get.id.get, insertedPool.get.id.get, Option("myWallet"), Option(1), previous, 0, 20, Option(UUID.randomUUID()))), Duration.Inf)
      fail()
    } catch {
      case e: DaemonDatabaseException => // previous cursor must be unique
    }
  }

  @Test def verifyAccountOperationNextUnique(): Unit = {
    val pubKey = "45A1994D8E33308DD08A3A8C937822101E229D85A2C0DFABC236A8C6A82E58076D"
    Await.result(dbDao.insertUser(UserDto(pubKey, 0)), Duration.Inf)
    val insertedUser = Await.result(dbDao.getUser(HexUtils.valueOf(pubKey)), Duration.Inf)
    Await.result(dbDao.insertPool(PoolDto("accountPool", insertedUser.get.id.get, "")), Duration.Inf)
    val insertedPool = (Await.result(dbDao.getPool(insertedUser.get.id.get, "accountPool"), Duration.Inf))
    val previous = Option(UUID.randomUUID())
    val next = Option(UUID.randomUUID())
    Await.result(dbDao.insertOperation(
      OperationDto(insertedUser.get.id.get, insertedPool.get.id.get, Option("myWallet"), Option(1), previous, 0, 20, next)), Duration.Inf)
    try {
      Await.result(dbDao.insertOperation(
        OperationDto(insertedUser.get.id.get, insertedPool.get.id.get, Option("myWallet"), Option(1), Option(UUID.randomUUID()), 0, 20, next)), Duration.Inf)
      fail()
    } catch {
      case e: DaemonDatabaseException => // next cursor must be unique
    }
  }

  @Test def verifyWalletOperationInsertion(): Unit = {
    val pubKey = "03B2394D8E33308DD0323A8C937322101E229D85A2C0DFABC236A8C6A82E58076D"
    Await.result(dbDao.insertUser(UserDto(pubKey, 0)), Duration.Inf)
    val insertedUser = Await.result(dbDao.getUser(HexUtils.valueOf(pubKey)), Duration.Inf)
    Await.result(dbDao.insertPool(PoolDto("accountPool", insertedUser.get.id.get, "")), Duration.Inf)
    val insertedPool = (Await.result(dbDao.getPool(insertedUser.get.id.get, "accountPool"), Duration.Inf))
    val previous = Option(UUID.randomUUID())
    val next = Option(UUID.randomUUID())
    Await.result(dbDao.insertOperation(
      OperationDto(insertedUser.get.id.get, insertedPool.get.id.get, Option("myWallet"), None, previous, 0, 20, next)), Duration.Inf)
    val preOp = Await.result(
      dbDao.getPreviousOperationInfo(next.get, insertedUser.get.id.get, insertedPool.get.id.get, Option("myWallet"), None), Duration.Inf)
    assert(preOp.isDefined)
    assert(previous === preOp.get.previous)
    assert(next === preOp.get.next)
    assert(0 === preOp.get.offset)
    assert(20 === preOp.get.batch)

    assert((Await.result(
      dbDao.getPreviousOperationInfo(next.get, insertedUser.get.id.get, insertedPool.get.id.get, Option("myWallet"), Option(1)), Duration.Inf)).isEmpty)
    assert((Await.result(
      dbDao.getPreviousOperationInfo(next.get, insertedUser.get.id.get, insertedPool.get.id.get, None, None), Duration.Inf)).isEmpty)

    assert(Await.result(dbDao.getNextOperationInfo(next.get, insertedUser.get.id.get, insertedPool.get.id.get, Option("myWallet"), None), Duration.Inf).isDefined)
    assert((Await.result(
      dbDao.getNextOperationInfo(next.get, insertedUser.get.id.get, insertedPool.get.id.get, Option("myWallet"), Option(1)), Duration.Inf)).isEmpty)
    assert((Await.result(
      dbDao.getNextOperationInfo(next.get, insertedUser.get.id.get, insertedPool.get.id.get, None, None), Duration.Inf)).isEmpty)
  }

  @Test def verifyAccountOperationInsertion(): Unit = {
    val pubKey = "13B2394D8E33308DD08A3A8C937822101E229D85A2C0DFABC236A8C6A82E58076D"
    Await.result(dbDao.insertUser(UserDto(pubKey, 0)), Duration.Inf)
    val insertedUser = Await.result(dbDao.getUser(HexUtils.valueOf(pubKey)), Duration.Inf)
    Await.result(dbDao.insertPool(PoolDto("accountPool", insertedUser.get.id.get, "")), Duration.Inf)
    val insertedPool = (Await.result(dbDao.getPool(insertedUser.get.id.get, "accountPool"), Duration.Inf))
    val previous = Option(UUID.randomUUID())
    val next = Option(UUID.randomUUID())
    Await.result(dbDao.insertOperation(
      OperationDto(insertedUser.get.id.get, insertedPool.get.id.get, Option("myWallet"), Option(1), previous, 0, 20, next)), Duration.Inf)
    val preOp = Await.result(
      dbDao.getPreviousOperationInfo(next.get, insertedUser.get.id.get, insertedPool.get.id.get, Option("myWallet"), Option(1)), Duration.Inf)
    val preW = Await.result(
      dbDao.getPreviousOperationInfo(next.get, insertedUser.get.id.get, insertedPool.get.id.get, Option("myWallet"), None), Duration.Inf)
    assert(preW.isEmpty)
    assert(preOp.isDefined)
    assert(previous === preOp.get.previous)
    assert(next === preOp.get.next)
    assert(0 === preOp.get.offset)
    assert(20 === preOp.get.batch)
    val preOp2 = Await.result(
      dbDao.getPreviousOperationInfo(next.get, insertedUser.get.id.get, insertedPool.get.id.get, Option("myWallet"), Option(1)), Duration.Inf)
    assert(preOp === preOp2)

    val nextOp = Await.result(
      dbDao.getNextOperationInfo(next.get, insertedUser.get.id.get, insertedPool.get.id.get, Option("myWallet"), Option(1)), Duration.Inf)
    val nextW = Await.result(
      dbDao.getNextOperationInfo(next.get, insertedUser.get.id.get, insertedPool.get.id.get, Option("myWallet"), None), Duration.Inf)
    assert(nextW.isEmpty)
    assert(nextOp.isDefined)
    assert(next === nextOp.get.previous)
    assert(20 === nextOp.get.offset)
    assert(20 === nextOp.get.batch)
    assert(nextOp.get.next.isDefined)

    val nextOp2 = Await.result(
      dbDao.getNextOperationInfo(next.get, insertedUser.get.id.get, insertedPool.get.id.get, Option("myWallet"), Option(1)), Duration.Inf)
    assertFalse(nextOp2.get.next === nextOp.get.next)
  }

  @Test def verifyUpdateoffset(): Unit = {
    val pubKey = "04133D4D8E33308DD08A3A8C937822101E229D85A2C0DFABC236A8C6A82E58076D"
    Await.result(dbDao.insertUser(UserDto(pubKey, 0)), Duration.Inf)
    val insertedUser = Await.result(dbDao.getUser(HexUtils.valueOf(pubKey)), Duration.Inf)
    Await.result(dbDao.insertPool(PoolDto("opsPool", insertedUser.get.id.get, "")), Duration.Inf)
    val insertedPool = (Await.result(dbDao.getPool(insertedUser.get.id.get, "opsPool"), Duration.Inf))
    val accountLevel = Option(UUID.randomUUID())
    val walletLevel = Option(UUID.randomUUID())
    val poolLevel = Option(UUID.randomUUID())
    Await.result(dbDao.insertOperation(
      OperationDto(insertedUser.get.id.get, insertedPool.get.id.get, Option("myWallet"), Option(1), Option(UUID.randomUUID()), 0, 20, accountLevel)), Duration.Inf)
    Await.result(dbDao.insertOperation(
      OperationDto(insertedUser.get.id.get, insertedPool.get.id.get, Option("myWallet"), None, Option(UUID.randomUUID()), 0, 20, walletLevel)), Duration.Inf)
    Await.result(dbDao.insertOperation(
      OperationDto(insertedUser.get.id.get, insertedPool.get.id.get, None, None, Option(UUID.randomUUID()), 0, 20, poolLevel)), Duration.Inf)
    val op1B = Await.result(
      dbDao.getPreviousOperationInfo(accountLevel.get, insertedUser.get.id.get, insertedPool.get.id.get, Option("myWallet"), Option(1)), Duration.Inf)
    assert(0 === op1B.get.offset)
    val op2B = Await.result(
      dbDao.getPreviousOperationInfo(walletLevel.get, insertedUser.get.id.get, insertedPool.get.id.get, Option("myWallet"), None), Duration.Inf)
    assert(0 === op2B.get.offset)
    val op3B = Await.result(
      dbDao.getPreviousOperationInfo(poolLevel.get, insertedUser.get.id.get, insertedPool.get.id.get, None, None), Duration.Inf)
    assert(0 === op3B.get.offset)
    Await.result(dbDao.updateOpsOffset(insertedPool.get.id.get, "myWallet", 1), Duration.Inf)
    val op1 = Await.result(
      dbDao.getPreviousOperationInfo(accountLevel.get, insertedUser.get.id.get, insertedPool.get.id.get, Option("myWallet"), Option(1)), Duration.Inf)
    assert(1 === op1.get.offset)
    val op2 = Await.result(
      dbDao.getPreviousOperationInfo(walletLevel.get, insertedUser.get.id.get, insertedPool.get.id.get, Option("myWallet"), None), Duration.Inf)
    assert(1 === op2.get.offset)
    val op3 = Await.result(
      dbDao.getPreviousOperationInfo(poolLevel.get, insertedUser.get.id.get, insertedPool.get.id.get, None, None), Duration.Inf)
    assert(1 === op3.get.offset)
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