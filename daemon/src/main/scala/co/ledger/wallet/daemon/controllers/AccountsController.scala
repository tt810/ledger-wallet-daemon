package co.ledger.wallet.daemon.controllers

import javax.inject.Inject

import co.ledger.wallet.daemon.exceptions.{InvalidArgumentException, WalletNotFoundException, WalletPoolNotFoundException}
import co.ledger.wallet.daemon.filters.AccountCreationFilter
import co.ledger.wallet.daemon.services.{AccountsService, LogMsgMaker}
import co.ledger.wallet.daemon.utils.RichRequest
import com.twitter.finagle.http.Request
import com.twitter.finatra.http.Controller
import com.twitter.finatra.request.RouteParam
import co.ledger.wallet.daemon.filters.AccountCreationContext._
import co.ledger.wallet.daemon.services.AuthenticationService.AuthentifiedUserContext._
import co.ledger.wallet.daemon.async.MDCPropagatingExecutionContext
import co.ledger.wallet.daemon.controllers.responses.ResponseSerializer

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
      case wnfe: WalletNotFoundException => responseSerializer.serializeNotFound(
        Map("response"->"Wallet doesn't exist", "wallet_name" -> request.wallet_name),
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
    accountsService.account(request.account_index.get, request.user, request.pool_name, request.wallet_name)
  }

  get("/pools/:pool_name/wallets/:wallet_name/accounts/next") { request: AccountRequest =>
    info(LogMsgMaker.newInstance("GET next account request")
      .append("request", request.request)
      .append("wallet_name", request.wallet_name)
      .append("pool_name", request.pool_name)
      .toString())
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
        case e: Throwable => responseSerializer.serializeInternalErrorToOk(response, e)
      }
  }

  delete("/pools/:pool_name/wallets/:wallet_name/accounts") { request: AccountRequest =>
    info(LogMsgMaker.newInstance("DELETE account request")
      .append("request", request.request)
      .append("wallet_name", request.wallet_name)
      .append("pool_name", request.pool_name)
      .toString())
  }

  private val responseSerializer: ResponseSerializer = ResponseSerializer.newInstance()
}

object AccountsController {
  case class AccountRequest(
                           @RouteParam pool_name: String,
                           @RouteParam wallet_name: String,
                           @RouteParam account_index: Option[Int],
                           request: Request
                           ) extends RichRequest(request)
}