package co.ledger.wallet.daemon.libledger_core.async

import co.ledger.core.{ExecutionContext, Lock, ThreadDispatcher}

class ScalaThreadDispatcher extends ThreadDispatcher {
  override def getSerialExecutionContext(name: String): ExecutionContext = ???
  override def getThreadPoolExecutionContext(name: String): ExecutionContext = ???
  override def getMainExecutionContext: ExecutionContext = ???
  override def newLock(): Lock = ???
}
