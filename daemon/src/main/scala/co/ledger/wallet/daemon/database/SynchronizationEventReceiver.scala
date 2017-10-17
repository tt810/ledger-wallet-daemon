package co.ledger.wallet.daemon.database

import co.ledger.core.{Event, EventCode, EventReceiver}
import co.ledger.wallet.daemon.services.LogMsgMaker
import com.twitter.inject.Logging

import scala.concurrent.Promise

class SynchronizationEventReceiver(accountIndex: Int, walletName: String, poolName: String, promise: Promise[SynchronizationResult]) extends EventReceiver with Logging {

  override def onEvent(event: Event): Unit = {
    info(LogMsgMaker.newInstance("Receive event")
      .append("event_code", event.getCode)
      .toString())
    if (EventCode.SYNCHRONIZATION_SUCCEED == event.getCode ||
    EventCode.SYNCHRONIZATION_SUCCEED_ON_PREVIOUSLY_EMPTY_ACCOUNT == event.getCode) promise.success(SynchronizationResult(accountIndex, walletName, poolName, true))
    else if (EventCode.SYNCHRONIZATION_FAILED == event.getCode) promise.success(SynchronizationResult(accountIndex, walletName, poolName, false))
  }
}

case class SynchronizationResult(accountIndex: Int, walletName: String, poolName: String, syncResult: Boolean)
