package co.ledger.wallet.daemon.controllers

import javax.inject.Inject

import co.ledger.wallet.daemon.async.MDCPropagatingExecutionContext
import co.ledger.wallet.daemon.controllers.responses.ResponseSerializer
import co.ledger.wallet.daemon.exceptions.{CurrencyNotFoundException, WalletNotFoundException, WalletPoolNotFoundException}
import co.ledger.wallet.daemon.services.{LogMsgMaker, WalletsService}
import co.ledger.wallet.daemon.utils.RichRequest
import com.fasterxml.jackson.annotation.JsonProperty
import com.twitter.finagle.http.Request
import com.twitter.finatra.http.Controller
import com.twitter.finatra.request.{QueryParam, RouteParam}
import com.twitter.finatra.validation.{MethodValidation, NotEmpty}

import scala.concurrent.ExecutionContext

class WalletsController @Inject()(walletsService: WalletsService) extends Controller {
  implicit val ec: ExecutionContext = MDCPropagatingExecutionContext.Implicits.global

  import WalletsController._

  get("/pools/:pool_name/wallets") {(request: GetWalletsRequest) =>
    info(LogMsgMaker.newInstance("GET wallets request")
      .append("request", request.request)
      .append("pool_name", request.pool_name)
      .toString())
    walletsService.wallets(
      request.user,
      request.pool_name,
      request.offset.getOrElse(0),
      request.count.getOrElse(20))
      .recover {
        case pnfe: WalletPoolNotFoundException => responseSerializer.serializeBadRequest(
          Map("response" -> "Wallet pool doesn't exist", "pool_name" -> request.pool_name),
          response,
          pnfe)
        case e: Throwable => responseSerializer.serializeInternalErrorToOk(response, e)
      }
  }

  get("/pools/:pool_name/wallets/:wallet_name") { request: GetWalletRequest =>
    info(LogMsgMaker.newInstance("GET wallet request")
      .append("request", request.request)
      .append("wallet_name", request.wallet_name)
      .append("pool_name", request.pool_name)
      .toString())
    walletsService.wallet(request.user, request.pool_name, request.wallet_name).recover {
      case pnfe: WalletPoolNotFoundException => responseSerializer.serializeBadRequest(
        Map("response" -> "Wallet pool doesn't exist", "pool_name" -> request.pool_name),
        response,
        pnfe)
      case wnfe: WalletNotFoundException => responseSerializer.serializeNotFound(
        Map("response"->"Wallet doesn't exist", "wallet_name" -> request.wallet_name),
        response,
        wnfe)
      case e: Throwable => responseSerializer.serializeInternalErrorToOk(response, e)
    }
  }

  post("/pools/:pool_name/wallets") {(request: CreateWalletRequest) =>
    info(LogMsgMaker.newInstance("CREATE wallet request")
      .append("request", request.request)
      .append("pool_name", request.pool_name)
      .toString())
    walletsService.createWallet(request.user, request.pool_name, request.wallet_name, request.currency_name).recover {
      case cnfe: CurrencyNotFoundException => responseSerializer.serializeBadRequest(
        Map("response"-> "Currency not support", "currency_name" -> request.currency_name),
        response,
        cnfe)
      case pnfe: WalletPoolNotFoundException => responseSerializer.serializeBadRequest(
        Map("response" -> "Wallet pool doesn't exist", "pool_name" -> request.pool_name),
        response,
        pnfe)
      case e: Throwable => responseSerializer.serializeInternalErrorToOk(response, e)
    }
  }

  private val responseSerializer: ResponseSerializer = ResponseSerializer.newInstance()
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
                                @NotEmpty @JsonProperty wallet_name: String,
                                @NotEmpty @JsonProperty currency_name: String,
                                request: Request
                                ) extends RichRequest(request) {
    @MethodValidation
    def validateWalletName =CommonMethodValidations.validateName("wallet_name", wallet_name)
  }
}