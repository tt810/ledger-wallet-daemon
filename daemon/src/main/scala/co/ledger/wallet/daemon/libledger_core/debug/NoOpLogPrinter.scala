package co.ledger.wallet.daemon.libledger_core.debug

import co.ledger.core.{ExecutionContext, LogPrinter}

class NoOpLogPrinter(ec: ExecutionContext) extends LogPrinter {
  override def printError(message: String): Unit = ()
  override def printInfo(message: String): Unit = ()
  override def printDebug(message: String): Unit = ()
  override def printWarning(message: String): Unit = ()
  override def printApdu(message: String): Unit = ()
  override def printCriticalError(message: String): Unit = ()
  override def getContext: ExecutionContext = ec
}
