package co.ledger.wallet.daemon.modules

import javax.inject.Singleton

import com.google.inject.Provides
import com.twitter.inject.TwitterModule
import com.typesafe.config.Config
import slick.jdbc.JdbcBackend.Database

object DatabaseModule extends TwitterModule {
  override val modules = Seq(ConfigurationModule)

  @Singleton
  @Provides
  def database (config: Config): Database = {
    val dbEngine = config.getString(DATABASE_ENGINE_KEY)
    info(s"Instantiating database with engine $dbEngine")
    Database.forConfig(dbEngine)
  }

  private val DATABASE_ENGINE_KEY = "database_engine"
}
