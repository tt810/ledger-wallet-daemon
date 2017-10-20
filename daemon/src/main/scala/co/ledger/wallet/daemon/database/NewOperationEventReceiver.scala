package co.ledger.wallet.daemon.database

import co.ledger.core._

class NewOperationEventReceiver(private val poolId: Long, private val db: DatabaseDao)
                               (implicit ec: scala.concurrent.ExecutionContext) extends EventReceiver {

  override def onEvent(event: Event): Unit = {
    if (EventCode.NEW_OPERATION == event.getCode)
      db.updateOpsOffset(poolId, event.getPayload.getString(Account.EV_NEW_OP_WALLET_NAME), event.getPayload.getInt(Account.EV_NEW_OP_ACCOUNT_INDEX))
  }
}
