package co.ledger.wallet.daemon.schedulers.observers

import co.ledger.core._
import co.ledger.wallet.daemon.database.OperationCache

class NewOperationEventReceiver(private val poolId: Long, private val opsCache: OperationCache) extends EventReceiver {

  override def onEvent(event: Event): Unit = {
    if (EventCode.NEW_OPERATION == event.getCode) {
      opsCache.updateOffset(
        poolId,
        event.getPayload.getString(Account.EV_NEW_OP_WALLET_NAME),
        event.getPayload.getInt(Account.EV_NEW_OP_ACCOUNT_INDEX))
    }
  }

}
