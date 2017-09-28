package co.ledger.wallet.daemon.libledger_core.debug

import co.ledger.core.{ExecutionContext, LogPrinter}

class NoOpLogPrinter(ec: ExecutionContext) extends LogPrinter {
  override def printError(message: String): Unit = ()//println(s"From core $message"))
  override def printInfo(message: String): Unit = ()//println(s"From core $message"))
  override def printDebug(message: String): Unit = ()//println(s"From core $message"))
  override def printWarning(message: String): Unit = ()//println(s"From core $message"))
  override def printApdu(message: String): Unit = ()//println(s"From core $message"))
  override def printCriticalError(message: String): Unit = ()//println(s"From core $message"))
  override def getContext: ExecutionContext = ec
}
