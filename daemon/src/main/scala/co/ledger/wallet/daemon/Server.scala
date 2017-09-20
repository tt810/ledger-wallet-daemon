package co.ledger.wallet.daemon

import co.ledger.wallet.daemon.controllers._
import co.ledger.wallet.daemon.database.{DatabaseDao, DatabaseInitializationRoutine}
import co.ledger.wallet.daemon.database.caches.DefaultDaemonCache
import co.ledger.wallet.daemon.filters.{AuthenticationFilter, DemoUserAuthenticationFilter, LWDAutenticationFilter}
import co.ledger.wallet.daemon.mappers.AuthenticationExceptionMapper
import co.ledger.wallet.daemon.modules.DaemonJacksonModule
import co.ledger.wallet.daemon.services.PoolsService
import com.twitter.finatra.http.HttpServer
import com.twitter.finatra.http.filters.CommonFilters
import com.twitter.finatra.http.routing.HttpRouter
import djinni.NativeLibLoader
import slick.jdbc.JdbcBackend.Database

object Server extends ServerImpl {

}

class ServerImpl extends HttpServer {


  override def jacksonModule = DaemonJacksonModule


  override protected def configureHttp(router: HttpRouter): Unit =
    router
          .filter[CommonFilters]
          .filter[DemoUserAuthenticationFilter]
          .filter[LWDAutenticationFilter]
          .add[AuthenticationFilter, AccountsController]
          .add[AuthenticationFilter, CurrenciesController]
          .add[AuthenticationFilter, StatusController]
          .add[AuthenticationFilter, WalletPoolsController]
          .add[AuthenticationFilter, WalletsController]
          .exceptionMapper[AuthenticationExceptionMapper]

  override protected def warmup(): Unit = {
    super.warmup()
    NativeLibLoader.loadLibs()
    try {
      DatabaseDao.migrate(Database.forConfig(DaemonConfiguration.dbProfileName))
      val daemonCache = injector.instance[DefaultDaemonCache](classOf[DefaultDaemonCache])
      DefaultDaemonCache.load(daemonCache)
      info("Initializing database, start to insert users...")
      injector.instance[DatabaseInitializationRoutine](classOf[DatabaseInitializationRoutine]).perform()
      info("Finished inserting users, start to obtain pools...")



      info("Finished obtaining pools")
    } catch {
      case _: Throwable => exitOnError(_)
    }

  }
}