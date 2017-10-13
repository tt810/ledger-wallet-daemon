package co.ledger.wallet.daemon.async

import java.util.concurrent.TimeUnit

import scala.concurrent.{ExecutionContext, Future}

object SerialExecutionContext {
  object Implicits{
    implicit lazy val global = SerialExecutionContextWrapper(ExecutionContext.Implicits.global)
  }

  def singleNamedThread(prefix: String) = {
    val threadPoolExecutor = new NamedThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS, prefix)
    SerialExecutionContextWrapper(ExecutionContext.fromExecutor(threadPoolExecutor))
  }
}

class SerialExecutionContextWrapper(implicit val ec: ExecutionContext) extends ExecutionContext with MDCPropagatingExecutionContext {
  private var _lastTask = Future.successful[Unit]()

  override def execute(runnable: Runnable): Unit = synchronized {
    _lastTask = _lastTask.map(_ => runnable.run())
  }

  override def reportFailure(cause: Throwable): Unit = ec.reportFailure(cause)
}

object SerialExecutionContextWrapper {
  def apply(implicit wrapped: ExecutionContext): SerialExecutionContextWrapper = {
    new SerialExecutionContextWrapper()
  }
}