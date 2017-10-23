package co.ledger.wallet.daemon.controllers

import javax.inject.Inject

import co.ledger.wallet.daemon.async.MDCPropagatingExecutionContext
import co.ledger.wallet.daemon.controllers.requests.{CommonMethodValidations, RichRequest}
import co.ledger.wallet.daemon.controllers.responses.ResponseSerializer
import co.ledger.wallet.daemon.exceptions.{CurrencyNotFoundException, WalletPoolNotFoundException}
import co.ledger.wallet.daemon.services.{LogMsgMaker, WalletsService}
import com.fasterxml.jackson.annotation.JsonProperty
import com.twitter.finagle.http.Request
import com.twitter.finatra.http.Controller
import com.twitter.finatra.request.{QueryParam, RouteParam}
import com.twitter.finatra.validation.{MethodValidation, NotEmpty, ValidationResult}

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
        case e: Throwable => responseSerializer.serializeInternalError(response, e)
      }
  }

  get("/pools/:pool_name/wallets/:wallet_name") { request: GetWalletRequest =>
    info(LogMsgMaker.newInstance("GET wallet request")
      .append("request", request.request)
      .append("wallet_name", request.wallet_name)
      .append("pool_name", request.pool_name)
      .toString())
    walletsService.wallet(request.user, request.pool_name, request.wallet_name).map { viewOpt =>
      viewOpt match {
        case Some(view) => responseSerializer.serializeOk(view, response)
        case None => responseSerializer.serializeNotFound(
          Map("response"->"Wallet doesn't exist", "wallet_name" -> request.wallet_name), response)
      }
    }.recover {
      case pnfe: WalletPoolNotFoundException => responseSerializer.serializeBadRequest(
        Map("response" -> "Wallet pool doesn't exist", "pool_name" -> request.pool_name),
        response,
        pnfe)
      case e: Throwable => responseSerializer.serializeInternalError(response, e)
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
      case e: Throwable => responseSerializer.serializeInternalError(response, e)
    }
  }

  private val responseSerializer: ResponseSerializer = ResponseSerializer.newInstance()
}

object WalletsController {

  case class GetWalletRequest(
                               @RouteParam pool_name: String,
                               @RouteParam wallet_name: String,
                               request: Request
                             ) extends RichRequest(request) {
    @MethodValidation
    def validatePoolName = CommonMethodValidations.validateName("pool_name", pool_name)

    @MethodValidation
    def validateWalletName = CommonMethodValidations.validateName("wallet_name", wallet_name)
  }

  case class GetWalletsRequest(
                              @RouteParam pool_name: String,
                              @QueryParam offset: Option[Int],
                              @QueryParam count: Option[Int],
                              request: Request
                              ) extends RichRequest(request) {
    @MethodValidation
    def validatePoolName = CommonMethodValidations.validateName("pool_name", pool_name)

    @MethodValidation
    def validateOffset = ValidationResult.validate(offset.isEmpty || offset.get >= 0, s"offset: offset can not be less than zero")

    @MethodValidation
    def validateCount = ValidationResult.validate(count.isEmpty || count.get > 0, s"account_index: index can not be less than 1")
  }

  case class CreateWalletRequest(
                                @RouteParam pool_name: String,
                                @NotEmpty @JsonProperty wallet_name: String,
                                @NotEmpty @JsonProperty currency_name: String,
                                request: Request
                                ) extends RichRequest(request) {
    @MethodValidation
    def validateWalletName = CommonMethodValidations.validateName("wallet_name", wallet_name)

    @MethodValidation
    def validatePoolName = CommonMethodValidations.validateName("pool_name", pool_name)
  }
}