package co.ledger.wallet.daemon.async

import scala.concurrent.{ExecutionContext, Future}

class SerialExecutionContext(implicit val ec: ExecutionContext) extends ExecutionContext {
  private var _lastTask = Future.successful[Unit]()

  override def execute(runnable: Runnable): Unit = synchronized {
    _lastTask = _lastTask.map(_ => runnable.run())
  }

  override def reportFailure(cause: Throwable): Unit = ec.reportFailure(cause)

}

object SerialExecutionContext {
  def newInstance() = new SerialExecutionContext()(scala.concurrent.ExecutionContext.Implicits.global)
}