package co.ledger.wallet.daemon.controllers

import javax.inject.Inject

import co.ledger.core.implicits.CurrencyNotFoundException
import co.ledger.wallet.daemon.{ErrorCode, ErrorResponseBody}
import co.ledger.wallet.daemon.services.WalletsService
import co.ledger.wallet.daemon.utils.RichRequest
import com.fasterxml.jackson.annotation.JsonProperty
import com.twitter.finagle.http.Request
import com.twitter.finatra.http.Controller
import com.twitter.finatra.request.{QueryParam, RouteParam}

import scala.concurrent.ExecutionContext.Implicits.global

class WalletsController @Inject()(walletsService: WalletsService) extends Controller {
  import WalletsController._

  get("/pools/:pool_name/wallets") { (request: GetWalletsRequest) =>
      walletsService.wallets(request.user, request.pool_name, request.offset.getOrElse(0), request.count.getOrElse(20))
      .recover {
        case e: Throwable => {
          error("Internal error", e)
          response.ok()
            .body(ErrorResponseBody(ErrorCode.Internal_Error, "Problem occurred when processing the request, check with developers"))
        }
      }
  }

  get("/pools/:pool_name/wallets/:wallet_name") { (request: GetWalletRequest) =>
    walletsService.wallet(request.user, request.pool_name, request.wallet_name)
      .recover {
        case e: Throwable => error(e)
      }
  }

  post("/pools/:pool_name/wallets") {(request: CreateWalletRequest) =>
    val currencyName = request.currency_name
    walletsService.createWallet(request.user, request.pool_name, request.wallet_name, currencyName)
      .recover {
        case cnfe: CurrencyNotFoundException => {
          debug("Invalid Request", cnfe)
          response.notFound()
            .body(ErrorResponseBody(ErrorCode.Invalid_Request, s"$currencyName is not a currency"))
        }
        case e: Throwable => {
          error("Internal error", e)
          response.ok()
            .body(ErrorResponseBody(ErrorCode.Internal_Error, "Problem occurred when processing the request, check with developers"))
        }
      }
  }

}

object WalletsController {
  case class GetWalletRequest(
                             @RouteParam pool_name: String,
                             @RouteParam wallet_name: String,
                             request: Request
                             ) extends RichRequest(request)

  case class GetWalletsRequest(
                              @RouteParam pool_name: String,
                              @QueryParam offset: Option[Int],
                              @QueryParam count: Option[Int],
                              request: Request
                              ) extends RichRequest(request)

  case class CreateWalletRequest(
                                @RouteParam pool_name: String,
                                @JsonProperty wallet_name: String,
                                @JsonProperty currency_name: String,
                                request: Request
                                ) extends RichRequest(request)

}