package co.ledger.wallet.daemon.controllers

import java.util.UUID
import javax.inject.Inject

import co.ledger.wallet.daemon.exceptions._
import co.ledger.wallet.daemon.filters.AccountCreationFilter
import co.ledger.wallet.daemon.services.{AccountsService, LogMsgMaker, OperationQueryParams}
import com.twitter.finagle.http.Request
import com.twitter.finatra.http.Controller
import com.twitter.finatra.request.{QueryParam, RouteParam}
import co.ledger.wallet.daemon.filters.AccountCreationContext._
import co.ledger.wallet.daemon.services.AuthenticationService.AuthentifiedUserContext._
import co.ledger.wallet.daemon.async.MDCPropagatingExecutionContext
import co.ledger.wallet.daemon.controllers.requests.{CommonMethodValidations, RichRequest}
import co.ledger.wallet.daemon.controllers.responses.ResponseSerializer
import com.twitter.finatra.validation.{MethodValidation, ValidationResult}

import scala.concurrent.ExecutionContext

class AccountsController @Inject()(accountsService: AccountsService) extends Controller {
  implicit val ec: ExecutionContext = MDCPropagatingExecutionContext.Implicits.global

  import AccountsController._

  get("/pools/:pool_name/wallets/:wallet_name/accounts") { request: AccountRequest =>
    info(LogMsgMaker.newInstance("GET accounts request")
      .append("request", request.request)
      .append("wallet_name", request.wallet_name)
      .append("pool_name", request.pool_name)
      .toString())
    accountsService.accounts(request.user, request.pool_name, request.wallet_name).recover {
      case pnfe: WalletPoolNotFoundException => responseSerializer.serializeBadRequest(
        Map("response" -> "Wallet pool doesn't exist", "pool_name" -> request.pool_name),
        response,
        pnfe)
      case wnfe: WalletNotFoundException => responseSerializer.serializeBadRequest(
        Map("response"->"Wallet doesn't exist", "wallet_name" -> request.wallet_name),
        response,
        wnfe)
      case e: Throwable => responseSerializer.serializeInternalErrorToOk(response, e)
    }
  }

  get("/pools/:pool_name/wallets/:wallet_name/accounts/next") { request: AccountCreationInfoRequest =>
    info(LogMsgMaker.newInstance("GET next account creation info request")
      .append("request", request.request)
      .append("wallet_name", request.wallet_name)
      .append("pool_name", request.pool_name)
      .append("account_index", request.account_index)
      .toString())
    accountsService.nextAccountCreationInfo(request.user, request.pool_name, request.wallet_name, request.account_index).recover {
      case pnfe: WalletPoolNotFoundException => responseSerializer.serializeBadRequest(
        Map("response" -> "Wallet pool doesn't exist", "pool_name" -> request.pool_name),
        response,
        pnfe)
      case wnfe: WalletNotFoundException => responseSerializer.serializeBadRequest(
        Map("response" -> "Wallet doesn't exist", "wallet_name" -> request.wallet_name),
        response,
        wnfe)
      case e: Throwable => responseSerializer.serializeInternalErrorToOk(response, e)
    }
  }

  get("/pools/:pool_name/wallets/:wallet_name/accounts/:account_index") { request: AccountRequest =>
    info(LogMsgMaker.newInstance("GET account request")
      .append("request", request.request)
      .append("account_index", request.account_index)
      .append("wallet_name", request.wallet_name)
      .append("pool_name", request.pool_name)
      .toString())
    accountsService.account(request.account_index.get, request.user, request.pool_name, request.wallet_name).recover {
      case pnfe: WalletPoolNotFoundException => responseSerializer.serializeBadRequest(
        Map("response" -> "Wallet pool doesn't exist", "pool_name" -> request.pool_name),
        response,
        pnfe)
      case wnfe: WalletNotFoundException => responseSerializer.serializeBadRequest(
        Map("response"->"Wallet doesn't exist", "wallet_name" -> request.wallet_name),
        response,
        wnfe)
      case anfe: AccountNotFoundException => responseSerializer.serializeNotFound(
        Map("response"->"Account doesn't exist", "account_index" -> request.account_index),
        response,
        anfe)
      case e: Throwable => responseSerializer.serializeInternalErrorToOk(response, e)
    }
  }

  get("/pools/:pool_name/wallets/:wallet_name/accounts/:account_index/operations") { request: OperationRequest =>
    info(LogMsgMaker.newInstance("GET account operation request")
      .append("request", request.request)
      .append("account_index", request.account_index)
      .append("previous_cursor", request.previous)
      .append("next_cursor", request.next)
      .append("batch", request.batch)
      .append("is_full_op", request.full_op > 0)
      .append("wallet_name", request.wallet_name)
      .append("pool_name", request.pool_name)
      .toString())
    accountsService.accountOperation(
      request.user,
      request.account_index,
      request.pool_name,
      request.wallet_name,
      OperationQueryParams(request.previous, request.next, request.batch, request.full_op)).recover {
        case pnfe: WalletPoolNotFoundException => responseSerializer.serializeBadRequest(
          Map("response" -> "Wallet pool doesn't exist", "pool_name" -> request.pool_name),
          response,
          pnfe)
        case onfe: OperationNotFoundException => responseSerializer.serializeBadRequest(
          Map("response" -> "Operation cursor doesn't exist", "next_cursor" -> request.next, "previous_cursor" -> request.previous),
          response,
          onfe)
        case wnfe: WalletNotFoundException => responseSerializer.serializeBadRequest(
          Map("response"->"Wallet doesn't exist", "wallet_name" -> request.wallet_name),
          response,
          wnfe)
        case anfe: AccountNotFoundException => responseSerializer.serializeBadRequest(
          Map("response"->"Account doesn't exist", "account_index" -> request.account_index),
          response,
          anfe)
        case e: Throwable => responseSerializer.serializeInternalErrorToOk(response, e)
      }
  }

  filter[AccountCreationFilter]
    .post("/pools/:pool_name/wallets/:wallet_name/accounts") { request: Request =>
      val walletName = request.getParam("wallet_name")
      val poolName = request.getParam("pool_name")
      info(LogMsgMaker.newInstance("CREATE account request")
        .append("request", request)
        .append("wallet_name", walletName)
        .append("pool_name", poolName)
        .toString())
      accountsService.createAccount(request.accountCreationBody,request.user.get,poolName,walletName).recover {
        case iae: InvalidArgumentException => responseSerializer.serializeBadRequest(
          Map("response"-> iae.msg, "pool_name" -> poolName, "wallet_name"->walletName),
          response,
          iae)
        case pnfe: WalletPoolNotFoundException => responseSerializer.serializeBadRequest(
          Map("response" -> "Wallet pool doesn't exist", "pool_name" -> poolName),
          response,
          pnfe)
        case wnfe: WalletNotFoundException => responseSerializer.serializeBadRequest(
          Map("response"->"Wallet doesn't exist", "wallet_name" -> walletName),
          response,
          wnfe)
        case e: Throwable => responseSerializer.serializeInternalErrorToOk(response, e)
      }
  }

  delete("/pools/:pool_name/wallets/:wallet_name/accounts") { request: AccountRequest =>
    info(LogMsgMaker.newInstance("DELETE account request")
      .append("request", request.request)
      .append("wallet_name", request.wallet_name)
      .append("pool_name", request.pool_name)
      .toString())
    //TODO
  }

  private val responseSerializer: ResponseSerializer = ResponseSerializer.newInstance()
}

object AccountsController {
  case class AccountRequest(
                           @RouteParam pool_name: String,
                           @RouteParam wallet_name: String,
                           @RouteParam account_index: Option[Int],
                           request: Request
                           ) extends RichRequest(request) {
    @MethodValidation
    def validatePoolName = CommonMethodValidations.validateName("pool_name", pool_name)

    @MethodValidation
    def validateWalletName = CommonMethodValidations.validateName("wallet_name", wallet_name)
  }

  case class AccountCreationInfoRequest(
                                         @RouteParam pool_name: String,
                                         @RouteParam wallet_name: String,
                                         @QueryParam account_index: Option[Int],
                                         request: Request
                                       ) extends RichRequest(request) {
    @MethodValidation
    def validatePoolName = CommonMethodValidations.validateName("pool_name", pool_name)

    @MethodValidation
    def validateWalletName = CommonMethodValidations.validateName("wallet_name", wallet_name)

    @MethodValidation
    def validateAccountIndex = CommonMethodValidations.validateOptionalAccountIndex(account_index)
  }
  case class OperationRequest(
                             @RouteParam pool_name: String,
                             @RouteParam wallet_name: String,
                             @RouteParam account_index: Int,
                             @QueryParam next: Option[UUID],
                             @QueryParam previous: Option[UUID],
                             @QueryParam batch: Int = 20,
                             @QueryParam full_op: Int = 0,
                             request: Request
                             ) extends RichRequest(request) {
    @MethodValidation
    def validatePoolName = CommonMethodValidations.validateName("pool_name", pool_name)

    @MethodValidation
    def validateWalletName = CommonMethodValidations.validateName("wallet_name", wallet_name)

    @MethodValidation
    def validateAccountIndex = ValidationResult.validate(account_index >= 0, s"account_index: index can not be less than zero")

    @MethodValidation
    def validateBatch = ValidationResult.validate(batch > 0, "batch: batch should be greater than zero")
  }
}