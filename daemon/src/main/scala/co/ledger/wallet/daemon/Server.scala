package co.ledger.wallet.daemon

import co.ledger.wallet.daemon.controllers._
import co.ledger.wallet.daemon.database.DefaultDaemonCache
import co.ledger.wallet.daemon.filters._
import co.ledger.wallet.daemon.mappers.AuthenticationExceptionMapper
import co.ledger.wallet.daemon.modules.DaemonJacksonModule
import co.ledger.wallet.daemon.services.UsersService
import com.twitter.finagle.http.{Request, Response}
import com.twitter.finatra.http.HttpServer
import com.twitter.finatra.http.filters.{AccessLoggingFilter, CommonFilters}
import com.twitter.finatra.http.routing.HttpRouter
import djinni.NativeLibLoader

import co.ledger.wallet.daemon.async.MDCPropagatingExecutionContext.Implicits.global

object Server extends ServerImpl {

}

class ServerImpl extends HttpServer {

  override def jacksonModule = DaemonJacksonModule

  override protected def configureHttp(router: HttpRouter): Unit =
    router
      .filter[LoggingMDCFilter[Request, Response]]
      .filter[TraceIdMDCFilter[Request, Response]]
      .filter[CommonFilters]
      .filter[AccessLoggingFilter[Request]]
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
    DefaultDaemonCache.migrateDatabase().flatMap { _ =>
      UsersService.initialize(injector.instance[UsersService](classOf[UsersService])).flatMap { _ =>
          DefaultDaemonCache.initialize()
      }
    }
  }
}