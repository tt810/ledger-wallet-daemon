package co.ledger.wallet.daemon.controllers

import javax.inject.Inject

import co.ledger.wallet.daemon.services.CurrenciesService
import co.ledger.wallet.daemon.swagger.DocumentedController
import co.ledger.wallet.daemon.utils.RichRequest
import com.twitter.finagle.http.Request
import com.twitter.finatra.request.RouteParam

class CurrenciesController @Inject() (currenciesService: CurrenciesService) extends DocumentedController {
  import CurrenciesController._

  get("/pools/:pool_name/currencies/:currency_name") { request: GetCurrencyRequest =>
    currenciesService.currency(request.user, request.pool_name, request.currency_name)
  }

  get("/pools/:pool_name/currencies") {request: GetCurrenciesRequest =>
    currenciesService.currencies(request.user, request.pool_name)
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

