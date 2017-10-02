package co.ledger.wallet.daemon.modules

import javax.inject.Singleton

import co.ledger.wallet.daemon.database.{DaemonCache, DefaultDaemonCache}
import com.google.inject.Provides
import com.twitter.inject.TwitterModule

object DaemonCacheModule extends TwitterModule {

  @Singleton
  @Provides
  def provideDaemonCache: DaemonCache = {
    new DefaultDaemonCache()
  }
}
