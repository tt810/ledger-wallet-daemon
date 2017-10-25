package co.ledger.wallet.daemon.modules

import javax.inject.Singleton

import co.ledger.wallet.daemon.DaemonConfiguration
import co.ledger.wallet.daemon.database.{DaemonCache, DefaultDaemonCache}
import co.ledger.wallet.daemon.schedulers.SynchronizationScheduler
import co.ledger.wallet.daemon.services.UsersService
import com.google.inject.Provides
import com.twitter.inject.{Injector, TwitterModule}

import scala.concurrent.Await
import scala.concurrent.duration._

object DaemonCacheModule extends TwitterModule {

  @Singleton
  @Provides
  def provideDaemonCache: DaemonCache = {
    val cache = new DefaultDaemonCache()
    val t0 = System.currentTimeMillis()
    Await.result(cache.dbMigration, 1.minutes)
    info(s"Database migration end, elapsed time: ${(System.currentTimeMillis() - t0)} milliseconds")
    cache
  }

  override def singletonPostWarmupComplete(injector: Injector): Unit = {
    val usersService = injector.instance[UsersService](classOf[UsersService])
    DaemonConfiguration.adminUsers.map { user =>
      val existingUser = Await.result(usersService.user(user._1, user._2), 1.minutes)
      if (existingUser.isEmpty) Await.result(usersService.createUser(user._1, user._2), 1.minutes)
    }
    DaemonConfiguration.whiteListUsers.map { user =>
      val existingUser = Await.result(usersService.user(user._1, user._2), 1.minutes)
      if (existingUser.isEmpty) Await.result(usersService.createUser(user._1, user._2), 1.minutes)
    }
    val scheduler = injector.instance[SynchronizationScheduler](classOf[SynchronizationScheduler])
    scheduler.schedule
  }
}
