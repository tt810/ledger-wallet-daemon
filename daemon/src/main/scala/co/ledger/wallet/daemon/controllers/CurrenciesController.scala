package co.ledger.wallet.daemon.controllers

import javax.inject.Inject

import co.ledger.core.implicits.CurrencyNotFoundException
import co.ledger.wallet.daemon.database.Pool
import co.ledger.wallet.daemon.exceptions.ResourceNotFoundException
import co.ledger.wallet.daemon.{ErrorCode, ErrorResponseBody}
import co.ledger.wallet.daemon.services.CurrenciesService
import co.ledger.wallet.daemon.swagger.DocumentedController
import co.ledger.wallet.daemon.utils.RichRequest
import com.twitter.finagle.http.Request
import com.twitter.finatra.request.RouteParam

import scala.concurrent.ExecutionContext.Implicits.global
import scala.reflect.ClassTag

class CurrenciesController @Inject() (currenciesService: CurrenciesService) extends DocumentedController {
  import CurrenciesController._

  get("/pools/:pool_name/currencies/:currency_name") { request: GetCurrencyRequest =>
    val poolName = request.pool_name
    val currencyName = request.currency_name
    currenciesService.currency(request.user, poolName, currencyName).recover {
      case pnfe: ResourceNotFoundException[ClassTag[Pool]] => {
        debug("Not Found", pnfe)
        response.badRequest()
          .body(ErrorResponseBody(ErrorCode.Invalid_Request, s"$poolName is not a pool"))
      }
      case cnfe: CurrencyNotFoundException => {
        debug("Not Found", cnfe)
        response.notFound()
          .body(ErrorResponseBody(ErrorCode.Not_Found, s"$currencyName is not a currency"))
      }
      case e: Throwable => {
        error("Internal error", e)
        response.ok()
          .body(ErrorResponseBody(ErrorCode.Internal_Error, "Problem occurred when processing the request, check with developers"))
      }
    }
  }

  get("/pools/:pool_name/currencies") {request: GetCurrenciesRequest =>
    val poolName = request.pool_name
    currenciesService.currencies(request.user, poolName).recover {
      case pnfe: ResourceNotFoundException[ClassTag[Pool]] => {
        debug("Not Found", pnfe)
        response.badRequest()
          .body(ErrorResponseBody(ErrorCode.Invalid_Request, s"$poolName is not a pool"))
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

