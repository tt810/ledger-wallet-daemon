package co.ledger.wallet.daemon.controllers

import javax.inject.Inject

import co.ledger.wallet.daemon.async.MDCPropagatingExecutionContext
import co.ledger.wallet.daemon.controllers.requests.{CommonMethodValidations, RichRequest}
import co.ledger.wallet.daemon.controllers.responses.ResponseSerializer
import co.ledger.wallet.daemon.exceptions._
import co.ledger.wallet.daemon.services.{CurrenciesService, LogMsgMaker}
import com.twitter.finagle.http.Request
import com.twitter.finatra.http.Controller
import com.twitter.finatra.request.RouteParam
import com.twitter.finatra.validation.MethodValidation

import scala.concurrent.ExecutionContext

class CurrenciesController @Inject()(currenciesService: CurrenciesService) extends Controller {
  implicit val ec: ExecutionContext = MDCPropagatingExecutionContext.Implicits.global
  import CurrenciesController._

  get("/pools/:pool_name/currencies/:currency_name") { request: GetCurrencyRequest =>
    val poolName = request.pool_name
    val currencyName = request.currency_name
    info(LogMsgMaker.newInstance("GET currency request")
      .append("request", request.request)
      .append("currency_name", currencyName)
      .append("pool_name", poolName)
      .toString())
    currenciesService.currency(currencyName, poolName, request.user.pubKey).map { currencyOpt =>
      currencyOpt match {
        case Some(currency) => responseSerializer.serializeOk(currency, response)
        case None => responseSerializer.serializeNotFound(
          Map("response"-> "Currency not support", "currency_name" -> currencyName), response)
      }
    }.recover {
      case pnfe: WalletPoolNotFoundException => responseSerializer.serializeBadRequest(
        Map("response" -> "Wallet pool doesn't exist", "pool_name" -> poolName),
        response,
        pnfe)
      case cnfe: CurrencyNotFoundException =>
      case e: Throwable => responseSerializer.serializeInternalError(response, e)
    }
  }

  get("/pools/:pool_name/currencies") {request: GetCurrenciesRequest =>
    val poolName = request.pool_name
    info(LogMsgMaker.newInstance("GET currencies request")
      .append("request", request.request)
      .append("pool_name", poolName)
      .toString())
    currenciesService.currencies(poolName, request.user.pubKey).recover {
      case pnfe: WalletPoolNotFoundException => responseSerializer.serializeBadRequest(
        Map("response" -> "Wallet pool doesn't exist", "pool_name" -> poolName),
        response,
        pnfe)
      case e: Throwable => responseSerializer.serializeInternalError(response, e)
    }
  }

  private val responseSerializer: ResponseSerializer = ResponseSerializer.newInstance()
}

object CurrenciesController {
  case class GetCurrenciesRequest(
                                 @RouteParam pool_name: String,
                                 request: Request
                                 ) extends RichRequest(request) {
    @MethodValidation
    def validatePoolName = CommonMethodValidations.validateName("pool_name", pool_name)
  }

  case class GetCurrencyRequest(
                               @RouteParam currency_name: String,
                               @RouteParam pool_name: String,
                               request: Request
                               ) extends RichRequest(request) {
    @MethodValidation
    def validatePoolName = CommonMethodValidations.validateName("pool_name", pool_name)
  }
}

