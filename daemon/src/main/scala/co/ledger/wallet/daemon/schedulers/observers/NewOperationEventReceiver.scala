package co.ledger.wallet.daemon.schedulers.observers

import co.ledger.core._
import co.ledger.wallet.daemon.database.DatabaseDao

import scala.concurrent.ExecutionContext

class NewOperationEventReceiver(private val poolId: Long, private val db: DatabaseDao)
                               (implicit ec: ExecutionContext) extends EventReceiver {

  override def onEvent(event: Event): Unit = {
    if (EventCode.NEW_OPERATION == event.getCode) {
      db.updateOpsOffset(
        poolId,
        event.getPayload.getString(Account.EV_NEW_OP_WALLET_NAME),
        event.getPayload.getInt(Account.EV_NEW_OP_ACCOUNT_INDEX))
    }
  }

}
