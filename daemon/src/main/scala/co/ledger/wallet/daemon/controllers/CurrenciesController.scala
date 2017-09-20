package co.ledger.wallet.daemon.controllers

import javax.inject.Inject

import co.ledger.core.implicits.CurrencyNotFoundException
import co.ledger.wallet.daemon.database.Pool
import co.ledger.wallet.daemon.exceptions.{ResourceAlreadyExistException, ResourceNotFoundException}
import co.ledger.wallet.daemon.filters.CurrencyDeserializeFilter
import co.ledger.wallet.daemon.models.Currency
import co.ledger.wallet.daemon.{ErrorCode, ErrorResponseBody}
import co.ledger.wallet.daemon.services.CurrenciesService
import co.ledger.wallet.daemon.utils.RichRequest
import com.twitter.finagle.http.Request
import com.twitter.finatra.http.Controller
import com.twitter.finatra.request.RouteParam
import co.ledger.wallet.daemon.filters.CurrencyContext._
import co.ledger.wallet.daemon.services.AuthenticationService.AuthentifiedUserContext._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.reflect.ClassTag

class CurrenciesController @Inject() (currenciesService: CurrenciesService) extends Controller {
  import CurrenciesController._

  get("/pools/:pool_name/currencies/:currency_name") { request: GetCurrencyRequest =>
    val poolName = request.pool_name
    val currencyName = request.currency_name
    currenciesService.currency(request.user, poolName, currencyName).recover {
      case pnfe: ResourceNotFoundException[ClassTag[Pool] @unchecked] => {
        debug("Not Found", pnfe)
        response.badRequest()
          .body(ErrorResponseBody(ErrorCode.Invalid_Request, s"$poolName doesn't exist"))
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
    val poolName = request.pool_name
    currenciesService.currencies(request.user, poolName).recover {
      case pnfe: ResourceNotFoundException[ClassTag[Pool] @unchecked] => {
        debug("Not Found", pnfe)
        response.badRequest()
          .body(ErrorResponseBody(ErrorCode.Invalid_Request, s"$poolName doesn't exist"))
      }
      case e: Throwable => {
        error("Internal error", e)
        response.ok()
          .body(ErrorResponseBody(ErrorCode.Internal_Error, "Problem occurred when processing the request, check with developers"))
      }
    }
  }

  filter[CurrencyDeserializeFilter]
    .post("/pools/:pool_name/currencies") { request: Request =>
    val poolName = request.getParam("pool_name")
    currenciesService.addCurrency(request.user.get, poolName, request.currency).recover {
      case pnfe: ResourceNotFoundException[ClassTag[Pool] @unchecked] => {
        debug("Not Found", pnfe)
        response.badRequest()
          .body(ErrorResponseBody(ErrorCode.Invalid_Request, s"$poolName doesn't exist"))
      }
      case alreadyExist: ResourceAlreadyExistException[ClassTag[Currency] @unchecked] => {
        debug("Duplicate request", alreadyExist)
        response.ok()
          .body(ErrorResponseBody(ErrorCode.Duplicate_Request, s"Attempt creating ${request.currency.name} request is ignored"))
      }
      case e: Throwable => {
        error("Internal error", e)
        response.ok()
          .body(ErrorResponseBody(ErrorCode.Internal_Error, "Problem occurred when processing the request, check with developers"))
      }
    }
  }

  delete("/pools/:pool_name/currencies/:currency_name") { request: Request =>
    val poolName = request.getParam("pool_name")
    val currencyName = request.getParam("currency_name")
    currenciesService.removeCurrency(request.user.get, poolName, currencyName).recover {
      case pnfe: ResourceNotFoundException[ClassTag[Pool] @unchecked] => {
        debug("Not Found", pnfe)
        response.badRequest()
          .body(ErrorResponseBody(ErrorCode.Invalid_Request, s"$poolName doesn't exist"))
      }
      case cnfe: CurrencyNotFoundException => {
        debug("Not Found", cnfe)
        response.ok()
          .body(ErrorResponseBody(ErrorCode.Not_Found, s"Currency $currencyName is not supported"))
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


