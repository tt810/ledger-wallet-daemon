package co.ledger.wallet.daemon.exceptions

class LedgerCoreException(val error: co.ledger.core.Error) extends Exception(error.getMessage) {
  def code = error.getCode
  def message = error.getMessage
}

object LedgerCoreException {
  def apply(error: co.ledger.core.Error) = new LedgerCoreException(error)
}