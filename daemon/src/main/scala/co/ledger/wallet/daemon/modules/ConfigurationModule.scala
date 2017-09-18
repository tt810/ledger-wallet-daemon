package co.ledger.wallet.daemon.modules

import com.google.inject.{Provides, Singleton}
import com.twitter.inject.TwitterModule
import com.typesafe.config.{Config, ConfigFactory}

object ConfigurationModule extends TwitterModule {
  @Singleton
  @Provides
  def configuration: Config = {
    ConfigFactory.load()
  }

}
