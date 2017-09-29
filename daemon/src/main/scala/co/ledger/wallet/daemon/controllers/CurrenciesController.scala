package co.ledger.wallet.daemon.controllers

import javax.inject.Inject

import co.ledger.wallet.daemon.exceptions._
import co.ledger.wallet.daemon.{ErrorCode, ErrorResponseBody}
import co.ledger.wallet.daemon.services.{CurrenciesService, LogMsgMaker}
import co.ledger.wallet.daemon.utils.RichRequest
import com.twitter.finagle.http.Request
import com.twitter.finatra.http.Controller
import com.twitter.finatra.request.RouteParam

import scala.concurrent.ExecutionContext.Implicits.global

class CurrenciesController @Inject() (currenciesService: CurrenciesService) extends Controller {
  import CurrenciesController._

  get("/pools/:pool_name/currencies/:currency_name") { request: GetCurrencyRequest =>
    val poolName = request.pool_name
    val currencyName = request.currency_name
    info(LogMsgMaker.newInstance("Receive get currencies request")
      .append("request", request)
      .toString())
    currenciesService.currency(currencyName, poolName).recover {
      case pnfe: WalletPoolNotFoundException => {
        debug("Not Found", pnfe)
        response.badRequest()
          .body(ErrorResponseBody(ErrorCode.Invalid_Request, s"Wallet pool $poolName doesn't exist"))
      }
      case cnfe: CurrencyNotFoundException => {
        debug("Not Found", cnfe)
        response.notFound()
          .body(ErrorResponseBody(ErrorCode.Not_Found, s"Currency $currencyName is not supported"))
      }
      case e: Throwable => {
        error("Internal error", e)
        response.ok()
          .body(ErrorResponseBody(ErrorCode.Internal_Error, "Problem occurred when processing the request, check with developers"))
      }
    }
  }

  get("/pools/:pool_name/currencies") {request: GetCurrenciesRequest =>
    info(LogMsgMaker.newInstance("Receive get currency request")
      .append("request", request)
      .toString())
    val poolName = request.pool_name
    currenciesService.currencies(poolName).recover {
      case pnfe: WalletPoolNotFoundException => {
        debug("Not Found", pnfe)
        response.badRequest()
          .body(ErrorResponseBody(ErrorCode.Invalid_Request, s"Wallet pool $poolName doesn't exist"))
      }
      case e: Throwable => {
        error("Internal error", e)
        response.ok()
          .body(ErrorResponseBody(ErrorCode.Internal_Error, "Problem occurred when processing the request, check with developers"))
      }
    }
  }
}

object CurrenciesController {
  case class GetCurrenciesRequest(
                                 @RouteParam pool_name: String,
                                 request: Request
                                 ) extends RichRequest(request)

  case class GetCurrencyRequest(
                               @RouteParam currency_name: String,
                               @RouteParam pool_name: String,
                               request: Request
                               ) extends RichRequest(request)
}

