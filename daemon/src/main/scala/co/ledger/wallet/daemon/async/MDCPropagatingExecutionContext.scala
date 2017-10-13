package co.ledger.wallet.daemon.async

import java.util.concurrent.Executors

import org.slf4j.MDC

import scala.concurrent.ExecutionContext

/**
  * Customized execution context to allow logback to propagate tracing information
  * from parent thread to children threads.
  */
trait MDCPropagatingExecutionContext extends ExecutionContext{
  self =>

  override def prepare(): ExecutionContext = new ExecutionContext {

    val context = MDC.getCopyOfContextMap

    override def execute(runnable: Runnable): Unit = self.execute(new Runnable {
      def run(): Unit = {

        val oldContext = MDC.getCopyOfContextMap

        try {
          if(context != null)
            MDC.setContextMap(context)
          else
            MDC.clear()
          runnable.run()
        } finally {
          if(oldContext != null)
            MDC.setContextMap(oldContext)
          else MDC.clear()
        }
      }
    })

    override def reportFailure(cause: Throwable): Unit = self.reportFailure(cause)
  }
}

object MDCPropagatingExecutionContext {
  object Implicits {
    implicit lazy val global = MDCPropagatingExecutionContextWrapper(ExecutionContext.Implicits.global)
  }

  def cachedNamedThreads(prefix: String) = {
    val threadPoolExecutor = Executors.newCachedThreadPool(Pools.newNamedThreadFactory(prefix))
    MDCPropagatingExecutionContextWrapper(ExecutionContext.fromExecutor(threadPoolExecutor))
  }
}

class MDCPropagatingExecutionContextWrapper(wrapped: ExecutionContext) extends ExecutionContext with MDCPropagatingExecutionContext {
  override def execute(runnable: Runnable): Unit = wrapped.execute(runnable)

  override def reportFailure(cause: Throwable): Unit = wrapped.reportFailure(cause)
}

object MDCPropagatingExecutionContextWrapper {
  def apply(wrapped: ExecutionContext): MDCPropagatingExecutionContextWrapper = {
    new MDCPropagatingExecutionContextWrapper(wrapped)
  }
}