package co.ledger.wallet.daemon

import co.ledger.core
import co.ledger.core.{Event, EventReceiver, ExecutionContext}
import co.ledger.wallet.daemon.database.DefaultDaemonCache
import co.ledger.wallet.daemon.libledger_core.async.LedgerCoreExecutionContext
import co.ledger.wallet.daemon.modules.DaemonCacheModule
import com.google.inject.Guice

import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Future, Promise}

object TestAccountPreparation {
  implicit val ec: ExecutionContext = LedgerCoreExecutionContext.newThreadPool()

  def prepare(accountIndex: Int, pubKey: String, poolName: String, walletName: String, promise: Promise[Boolean]): Future[Boolean] = {
    val cache = Guice.createInjector(DaemonCacheModule).getInstance(classOf[DefaultDaemonCache])
    val coreAccount = Await.result(cache.getCoreAccount(accountIndex, pubKey, poolName, walletName), Duration.Inf)
    prepare(coreAccount._1, promise)
  }

  def prepare(account: core.Account, promise: Promise[Boolean]): Future[Boolean] = {
    val receiver = new DaemonEventReceiver(promise)
    account.synchronize().subscribe(ec, receiver)
    promise.future
  }

  class DaemonEventReceiver(promise: Promise[Boolean]) extends EventReceiver {

    override def onEvent(event: Event): Unit = {
      if(event.getCode == core.EventCode.SYNCHRONIZATION_SUCCEED ||
        event.getCode == core.EventCode.SYNCHRONIZATION_SUCCEED_ON_PREVIOUSLY_EMPTY_ACCOUNT) {
        promise.success(true)
      }
    }
  }

}
