package co.ledger.wallet.daemon

import co.ledger.core
import co.ledger.core.{Event, EventReceiver, ExecutionContext}
import co.ledger.wallet.daemon.libledger_core.async.LedgerCoreExecutionContext

import scala.concurrent.{Future, Promise}

object TestAccountPreparation {
  implicit val ec: ExecutionContext = LedgerCoreExecutionContext.newThreadPool()

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
