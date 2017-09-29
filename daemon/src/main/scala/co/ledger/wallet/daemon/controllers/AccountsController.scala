package co.ledger.wallet.daemon.controllers

import javax.inject.Inject

import co.ledger.wallet.daemon.{ErrorCode, ErrorResponseBody}
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

import scala.concurrent.ExecutionContext

class AccountsController @Inject()(accountsService: AccountsService) extends Controller {
  implicit val ec: ExecutionContext = MDCPropagatingExecutionContext.Implicits.global

  import AccountsController._

  get("/pools/:pool_name/wallets/:wallet_name/accounts") { request: AccountRequest =>
    info(LogMsgMaker.newInstance("Receive get accounts request")
      .append("request", request)
      .toString())
    accountsService.accounts(request.user, request.pool_name, request.wallet_name).recover {
      case pnfe: WalletPoolNotFoundException => {
        debug("Invalid Request", pnfe)
        response.badRequest()
          .body(ErrorResponseBody(ErrorCode.Invalid_Request, s"Wallet pool ${request.pool_name} doesn't exist"))
      }
      case wnfe: WalletNotFoundException => {
        debug("Invalid Request", wnfe)
        response.badRequest()
          .body(ErrorResponseBody(ErrorCode.Invalid_Request, s"Wallet ${request.wallet_name} doesn't exist"))
      }
      case e: Throwable => {
        error("Internal error", e)
        response.ok()
          .body(ErrorResponseBody(ErrorCode.Internal_Error, "Problem occurred when processing the request, check with developers"))
      }
    }
  }

  get("/pools/:pool_name/wallets/:wallet_name/accounts/:account_index") { request: AccountRequest =>
    info(LogMsgMaker.newInstance("Receive get account request")
      .append("request", request)
      .toString())
    accountsService.account(request.account_index.get, request.user, request.pool_name, request.wallet_name)
  }

  get("/pools/:pool_name/wallets/:wallet_name/accounts/next") { request: AccountRequest =>
    info(LogMsgMaker.newInstance("Receive get next account request")
      .append("request", request)
      .toString())
  }

  filter[AccountCreationFilter]
    .post("/pools/:pool_name/wallets/:wallet_name/accounts") { request: Request =>
      info(LogMsgMaker.newInstance("Receive create account request")
        .append("request", request)
        .append("body", request.contentString)
        .toString())
      accountsService.createAccount(
        request.accountCreationBody,
        request.user.get,
        request.getParam("pool_name"),
        request.getParam("wallet_name")
      ).recover {
        case iae: InvalidArgumentException => {
          debug("Invalid Request", iae)
          response.badRequest()
            .body(ErrorResponseBody(ErrorCode.Invalid_Request, iae.msg))
        }
        case e: Throwable => {
          error("Internal error", e)
          response.ok()
            .body(ErrorResponseBody(ErrorCode.Internal_Error, "Problem occurred when processing the request, check with developers"))
        }
      }
  }

  delete("/pools/:pool_name/wallets/:wallet_name/accounts") { request: AccountRequest =>
    info(LogMsgMaker.newInstance("Receive delete account request")
      .append("request", request)
      .toString())
  }
}

object AccountsController {
  case class AccountRequest(
                           @RouteParam pool_name: String,
                           @RouteParam wallet_name: String,
                           @RouteParam account_index: Option[Int],
                           request: Request
                           ) extends RichRequest(request)
}