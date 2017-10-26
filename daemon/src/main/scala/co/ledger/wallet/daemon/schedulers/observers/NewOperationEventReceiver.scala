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

  private def canEqual(a: Any): Boolean = a.isInstanceOf[NewOperationEventReceiver]

  override def equals(that: Any): Boolean = that match {
    case that: NewOperationEventReceiver => that.canEqual(this) && this.hashCode() == that.hashCode()
    case _ => false
  }

  override def hashCode(): Int = {
    poolId.hashCode()
  }
}
