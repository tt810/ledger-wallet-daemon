package co.ledger.wallet.daemon.schedulers

import java.util.concurrent.TimeUnit
import javax.inject.{Inject, Singleton}

import co.ledger.wallet.daemon.DaemonConfiguration
import co.ledger.wallet.daemon.async.MDCPropagatingExecutionContext
import co.ledger.wallet.daemon.database.DaemonCache
import com.twitter.concurrent.NamedPoolThreadFactory
import com.twitter.inject.Logging
import com.twitter.util._

import scala.concurrent.duration.{Duration => NativeDuration}
import scala.concurrent.{ExecutionContext, Await => NativeAwait}
import scala.util.{Failure, Success}

@Singleton
class SynchronizationScheduler @Inject()(daemonCache: DaemonCache) extends Timer with Logging {
  implicit val ec: ExecutionContext = MDCPropagatingExecutionContext.cachedNamedThreads("scheduler-thread-pool")

  lazy val schedule = scheduleSync(Time.fromSeconds(DaemonConfiguration.synchronizationInterval._1), Duration(DaemonConfiguration.synchronizationInterval._2, TimeUnit.SECONDS))

  def scheduleSync(when: Time, period: Duration) = schedulePeriodically(when: Time, period: Duration)(synchronizationTask(daemonCache))

  override def schedulePeriodically(when: Time, period: Duration)(f: => Unit): TimerTask =
    scheduledThreadPoolTimer.schedule(when, period)(f)

  override protected def scheduleOnce(when: Time)(f: => Unit): TimerTask =
    throw new Exception("One time scheduling not supported")

  override def stop(): Unit = scheduledThreadPoolTimer.stop()

  private def synchronizationTask(daemonCache: DaemonCache): Unit = try {
    val t0 = System.currentTimeMillis()
    NativeAwait.ready(daemonCache.syncOperations(), NativeDuration.Inf).onComplete {
      case Success(result) => {
        result.map { r =>
          if (r.syncResult) info(s"Synchronization complete for $r")
          else warn(s"Failed synchronizing $r")
        }
        val t1 = System.currentTimeMillis()
        info(s"Synchronization finished, elapsed time: ${(t1 - t0)} milliseconds")
      }
      case Failure(e) => {
        val t1 = System.currentTimeMillis()
        info(s"Synchronization finished, elapsed time: ${(t1 - t0)} milliseconds")
        e
      }
    }
  } catch {
    case e: Throwable => error(s"Unexpected runtime exception during synchronization", e)
  }

  private val scheduledThreadPoolTimer = new ScheduledThreadPoolTimer(
    poolSize = 2,
    threadFactory = new NamedPoolThreadFactory("scheduler-thread-pool")
  )
}
