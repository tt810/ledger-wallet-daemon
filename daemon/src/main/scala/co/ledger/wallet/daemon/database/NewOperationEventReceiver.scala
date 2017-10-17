package co.ledger.wallet.daemon.database

import co.ledger.core._
import co.ledger.wallet.daemon.services.LogMsgMaker
import com.twitter.inject.Logging

class NewOperationEventReceiver(private val poolId: Long, private val db: DatabaseDao)
                               (implicit ec: scala.concurrent.ExecutionContext) extends EventReceiver with Logging {

  override def onEvent(event: Event) = {
    info(LogMsgMaker.newInstance("Receive event")
      .append("event_code", event.getCode)
      .toString())
    if (EventCode.NEW_OPERATION == event.getCode)
      db.updateOpsOffset(poolId, event.getPayload.getString(Account.EV_NEW_OP_WALLET_NAME), event.getPayload.getInt(Account.EV_NEW_OP_ACCOUNT_INDEX))
  }
}
