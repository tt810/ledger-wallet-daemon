package co.ledger.wallet.daemon

import co.ledger.wallet.daemon.controllers._
import co.ledger.wallet.daemon.database.DefaultDaemonCache
import co.ledger.wallet.daemon.filters.{AuthenticationFilter, DemoUserAuthenticationFilter, LWDAutenticationFilter}
import co.ledger.wallet.daemon.mappers.AuthenticationExceptionMapper
import co.ledger.wallet.daemon.services.UsersService
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
    import scala.concurrent.ExecutionContext.Implicits.global
    super.warmup()
    NativeLibLoader.loadLibs()
    DefaultDaemonCache.migrateDatabase().flatMap { _ =>
      UsersService.initialize(injector.instance[UsersService](classOf[UsersService])).flatMap { _ =>
          DefaultDaemonCache.initialize()
      }
    }
  }
}