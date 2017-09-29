package co.ledger.wallet.daemon.controllers

import javax.inject.Inject

import co.ledger.wallet.daemon.async.MDCPropagatingExecutionContext
import co.ledger.wallet.daemon.exceptions._
import co.ledger.wallet.daemon.{ErrorCode, ErrorResponseBody}
import co.ledger.wallet.daemon.services.{LogMsgMaker, PoolsService}
import com.twitter.finagle.http.Request
import co.ledger.wallet.daemon.services.AuthenticationService.AuthentifiedUserContext._
import co.ledger.wallet.daemon.services.PoolsService.PoolConfiguration
import co.ledger.wallet.daemon.utils.RichRequest
import com.fasterxml.jackson.annotation.JsonProperty
import com.twitter.finatra.http.Controller

import scala.concurrent.ExecutionContext

class WalletPoolsController @Inject()(poolsService: PoolsService) extends Controller {
  implicit val ec: ExecutionContext = MDCPropagatingExecutionContext.Implicits.global
  import WalletPoolsController._

  get("/pools") {(request: Request) =>
    info(LogMsgMaker.newInstance("Receive get wallet pools request")
      .append("request", request)
      .toString())
    poolsService.pools(request.user.get).recover {
      case e: Throwable => {
        error("Internal error", e)
        response.ok()
          .body(ErrorResponseBody(ErrorCode.Internal_Error, "Problem occurred when processing the request, check with developers"))
      }
    }
  }

  get("/pools/:pool_name") {(request: Request) =>
    info(LogMsgMaker.newInstance("Receive get wallet pool request")
      .append("request", request)
      .toString())
    val poolName = request.getParam("pool_name")
    poolsService.pool(request.user.get, poolName).recover {
      case pe: WalletPoolNotFoundException => {
        debug("Not Found", pe)
        response.notFound()
          .body(ErrorResponseBody(ErrorCode.Not_Found, s"Wallet pool $poolName doesn't exist"))
      }
      case e: Throwable => {
        error("Internal error", e)
        response.ok()
          .body(ErrorResponseBody(ErrorCode.Internal_Error, "Problem occurred when processing the request, check with developers"))
      }
    }
  }

  post("/pools") { (request: CreationRequest) =>
    info(LogMsgMaker.newInstance("Receive create wallet pool request")
      .append("request", request)
      .toString())
    val poolName = request.pool_name
    // TODO: Deserialize the configuration from the body of the request
    poolsService.createPool(request.user, poolName, PoolConfiguration()).recover {
      case e: Throwable => {
        error("Internal error", e)
        response.ok()
          .body(ErrorResponseBody(ErrorCode.Internal_Error, "Problem occurred when processing the request, check with developers"))
      }
    }
  }

  delete("/pools/:pool_name") {(request: Request) =>
    info(LogMsgMaker.newInstance("Receive delete wallet pools request")
      .append("request", request)
      .toString())
    val poolName = request.getParam("pool_name")
    poolsService.removePool(request.user.get, poolName).recover {
      case pe: WalletPoolNotFoundException => {
       debug("Not Found", pe)
       response.notFound()
          .body(ErrorResponseBody(ErrorCode.Invalid_Request, s"Attempt deleting wallet pool $poolName request is ignored"))
      }
      case e: Throwable => {
        error("Internal error", e)
        response.ok()
          .body(ErrorResponseBody(ErrorCode.Internal_Error, "Problem occurred when processing the request, check with developers"))
      }
    }
  }

}

object WalletPoolsController {
  case class CreationRequest(
                            @JsonProperty pool_name: String,
                            request: Request
                            ) extends RichRequest(request)
}