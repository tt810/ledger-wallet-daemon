package co.ledger.wallet.daemon.controllers

import javax.inject.Inject

import co.ledger.wallet.daemon.exceptions.{CurrencyNotFoundException, WalletNotFoundException, WalletPoolNotFoundException}
import co.ledger.wallet.daemon.{ErrorCode, ErrorResponseBody}
import co.ledger.wallet.daemon.services.{LogMsgMaker, WalletsService}
import co.ledger.wallet.daemon.utils.RichRequest
import com.fasterxml.jackson.annotation.JsonProperty
import com.twitter.finagle.http.Request
import com.twitter.finatra.http.Controller
import com.twitter.finatra.request.{QueryParam, RouteParam}

import scala.concurrent.ExecutionContext.Implicits.global

class WalletsController @Inject()(walletsService: WalletsService) extends Controller {
  import WalletsController._

  get("/pools/:pool_name/wallets") {(request: GetWalletsRequest) =>
    info(LogMsgMaker.newInstance("Receive get wallets request")
      .append("request", request)
      .toString())
    walletsService.wallets(
      request.user,
      request.pool_name,
      request.offset.getOrElse(0),
      request.count.getOrElse(20))
      .recover {
        case pnfe: WalletPoolNotFoundException => {
          debug("Invalid Request", pnfe)
          response.badRequest()
            .body(ErrorResponseBody(ErrorCode.Invalid_Request, s"Wallet pool ${request.pool_name} doesn't exist"))
        }
        case e: Throwable => {
          error("Internal error", e)
          response.ok()
            .body(ErrorResponseBody(ErrorCode.Internal_Error, "Problem occurred when processing the request, check with developers"))
        }
      }
  }

  get("/pools/:pool_name/wallets/:wallet_name") { request: GetWalletRequest =>
    info(LogMsgMaker.newInstance("Receive get wallet request")
      .append("request", request)
      .toString())
    walletsService.wallet(request.user, request.pool_name, request.wallet_name).recover {
      case pnfe: WalletPoolNotFoundException => {
        debug("Invalid Request", pnfe)
        response.badRequest()
          .body(ErrorResponseBody(ErrorCode.Invalid_Request, s"Wallet pool ${request.pool_name} doesn't exist"))
      }
      case wnfe: WalletNotFoundException => {
        debug("Not Found", wnfe)
        response.notFound()
          .body(ErrorResponseBody(ErrorCode.Not_Found, s"Wallet ${request.wallet_name} doesn't exist"))
      }
      case e: Throwable => {
        error("Internal error", e)
        response.ok()
          .body(ErrorResponseBody(ErrorCode.Internal_Error, "Problem occurred when processing the request, check with developers"))
      }
    }
  }

  post("/pools/:pool_name/wallets") {(request: CreateWalletRequest) =>
    info(LogMsgMaker.newInstance("Receive create wallet request")
      .append("request", request)
      .toString())
    walletsService.createWallet(request.user, request.pool_name, request.wallet_name, request.currency_name).recover {
      case cnfe: CurrencyNotFoundException => {
        debug("Invalid Request", cnfe)
        response.badRequest()
          .body(ErrorResponseBody(ErrorCode.Invalid_Request, s"Currency ${request.currency_name} is not supported"))
      }
      case pnfe: WalletPoolNotFoundException => {
        debug("Invalid Request", pnfe)
        response.badRequest()
          .body(ErrorResponseBody(ErrorCode.Invalid_Request, s"Wallet pool ${request.pool_name} doesn't exist"))
      }
      case e: Throwable =>
        error("Internal error", e)
        response.ok()
          .body(ErrorResponseBody(ErrorCode.Internal_Error, "Problem occurred when processing the request, check with developers"))
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