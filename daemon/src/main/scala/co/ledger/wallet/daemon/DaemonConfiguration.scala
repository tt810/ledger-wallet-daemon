package co.ledger.wallet.daemon

import java.util.Locale

import com.typesafe.config.ConfigFactory
import slick.jdbc.JdbcProfile

import scala.collection.JavaConverters._
import scala.collection.mutable.ListBuffer
import scala.util.Try

object DaemonConfiguration {
  private val config = ConfigFactory.load()
  private val PERMISSION_CREATE_USER: Int = 0x01

  val adminUsers: Seq[(String, String)] = if (config.hasPath("demo_users")) {
    val usersConfig = config.getConfigList("demo_users").asScala.toSeq
    for {
      userConfig <- usersConfig
    } yield (userConfig.getString("username"), userConfig.getString("password"))

  } else List[(String, String)]()

  val whiteListUsers: Seq[(String, Long)] = if (config.hasPath("whitelist")){
    val usersConfig = config.getConfigList("whitelist")
    var users = new ListBuffer[(String, Long)]()
    for (i <- 0 until usersConfig.size()) {
      val userConfig = usersConfig.get(i)
      val pubKey = userConfig.getString("key").toUpperCase(Locale.US)
      val permissions = if (Try(userConfig.getBoolean("account_creation")).getOrElse(false)) PERMISSION_CREATE_USER else 0
      users += ((pubKey, permissions))
    }
    users.toList
  } else List[(String, Long)]()

  val authTokenDuration: Int =
    if (config.hasPath("authentication.token_duration")) config.getInt("authentication.token_duration") * 1000
    else 30000

  val dbProfileName: String = Try(config.getString("database_engine")).toOption.getOrElse("sqlite3")

  val dbProfile: JdbcProfile = dbProfileName match {
    case "sqlite3" =>
      slick.jdbc.SQLiteProfile
    case "postgres" =>
      slick.jdbc.PostgresProfile
    case "h2mem1" =>
      slick.jdbc.H2Profile
    case others => throw new Exception(s"Unknown database backend $others")
  }

  val synchronizationInterval: (Int, Int) = (
    if (config.hasPath("synchronization.initial_delay_in_seconds")) config.getInt("synchronization.initial_delay_in_seconds")
    else 5 * 60,
    if(config.hasPath("synchronization.interval_in_hours")) config.getInt("synchronization.interval_in_hours")
    else 24)

  val realtimeObserverOn: Boolean =
    if (config.hasPath("realtimeobservation")) config.getBoolean("realtimeobservation")
    else false

}
