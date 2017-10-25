package co.ledger.wallet.daemon.schedulers

import java.util.UUID

import co.ledger.wallet.daemon.async.MDCPropagatingExecutionContext.Implicits.global
import co.ledger.wallet.daemon.database.{DefaultDaemonCache, UserDto}
import co.ledger.wallet.daemon.models.{AccountDerivationView, DerivationView}
import com.twitter.inject.Logging
import com.twitter.util.MockTimer
import djinni.NativeLibLoader
import org.junit.Test
import org.scalatest.junit.AssertionsForJUnit

import scala.concurrent.Await
import scala.concurrent.duration.{Duration => NativeDuration}

class SynchronizationSchedulerTest extends AssertionsForJUnit with Logging {
  import com.twitter.util.{Duration, Time}

  @Test def verifySchedule(): Unit = Time.withCurrentTimeFrozen { timeControl =>
    val timer = new MockTimer()
    val cache = new DefaultDaemonCache()
    Await.result(cache.dbMigration, NativeDuration.Inf)
    val schedule = timer.doAt(Time.now) {
      val scheduler = new SynchronizationScheduler(cache)
      scheduler.scheduleSync(Time.fromMilliseconds(1), Duration.fromSeconds(5))
    }
    timeControl.advance(Duration.fromMilliseconds(1))
    timer.tick()
    assert(schedule.isDefined)

    val isPrepared = timer.doAt(Time.fromMilliseconds(2)) {
      val u = schedule.map { _ =>
        NativeLibLoader.loadLibs()
        val user = for {
          id <- cache.createUser(UserDto(PUB_KEY_1, 0, None))
          user <- cache.getUser(PUB_KEY_1)
          pool <- cache.createWalletPool(user.get, POOL_NAME, "")
          wallet <- cache.createWallet(WALLET_NAME, "bitcoin", POOL_NAME, user.get)
        } yield user
        Await.result(user, NativeDuration.Inf)
      }
      com.twitter.util.Await.result(u)
    }

    timeControl.advance(Duration.fromMilliseconds(2))
    timer.tick()
    assert(isPrepared.isDefined)

    val insertion = timer.doAt(Time.fromMilliseconds(5)) {
      val o = isPrepared.map { userOp =>
        assert(userOp.isDefined)
        val op =  cache.createAccount(
          AccountDerivationView(0, List(
            DerivationView("44'/0'/0'", "main", Option("0437bc83a377ea025e53eafcd18f299268d1cecae89b4f15401926a0f8b006c0f7ee1b995047b3e15959c5d10dd1563e22a2e6e4be9572aa7078e32f317677a901"), Option("d1bb833ecd3beed6ec5f6aa79d3a424d53f5b99147b21dbc00456b05bc978a71")),
            DerivationView("44'/0'", "main", Option("0437bc83a377ea025e53eafcd18f299268d1cecae89b4f15401926a0f8b006c0f7ee1b995047b3e15959c5d10dd1563e22a2e6e4be9572aa7078e32f317677a901"), Option("d1bb833ecd3beed6ec5f6aa79d3a424d53f5b99147b21dbc00456b05bc978a71")))),
          userOp.get,
          POOL_NAME,
          WALLET_NAME).flatMap { account =>
          cache.getAccountOperations(userOp.get, 0, POOL_NAME, WALLET_NAME, 1, 1)
        }
        Await.result(op, NativeDuration.Inf)
      }
      com.twitter.util.Await.result(o)
    }
    timeControl.advance(Duration.fromMilliseconds(5))
    timer.tick()
    assert(insertion.isDefined)

    val secondTimeSync = insertion.map { operation =>
      assert(operation.next.isEmpty)
      assert(operation.previous.isEmpty)
      assert(operation.operations.size === 0)
    }
    com.twitter.util.Await.result(secondTimeSync)
    timeControl.advance(Duration.fromSeconds(5))
    timer.tick()
//    Time.sleep(Duration.fromSeconds(1))
  }
  private val PUB_KEY_1 = UUID.randomUUID().toString
  private val WALLET_NAME = UUID.randomUUID().toString
  private val POOL_NAME = UUID.randomUUID().toString
}
