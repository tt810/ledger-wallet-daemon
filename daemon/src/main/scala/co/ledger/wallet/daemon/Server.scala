package co.ledger.wallet.daemon

import co.ledger.wallet.daemon.controllers._
import co.ledger.wallet.daemon.database.DatabaseInitializationRoutine
import co.ledger.wallet.daemon.filters.{AuthenticationFilter, DemoUserAuthenticationFilter, LWDAutenticationFilter}
import co.ledger.wallet.daemon.mappers.AuthenticationExceptionMapper
import co.ledger.wallet.daemon.services.PoolsService
import com.twitter.finatra.http.HttpServer
import com.twitter.finatra.http.filters.CommonFilters
import com.twitter.finatra.http.routing.HttpRouter
import djinni.NativeLibLoader

object Server extends ServerImpl {

}

class ServerImpl extends HttpServer {

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
      info("Initializing database, start to insert users...")
      injector.instance[DatabaseInitializationRoutine](classOf[DatabaseInitializationRoutine]).perform()
      info("Finished inserting users, start to obtain pools...")
      val poolsService = injector.instance[PoolsService](classOf[PoolsService])
      poolsService.initialize()
      info("Finished obtaining pools")
    } catch {
      case _: Throwable => exitOnError(_)
    }

  }
}