package co.ledger.wallet.daemon.controllers

import javax.inject.Inject

import co.ledger.wallet.daemon.database.Pool
import co.ledger.wallet.daemon.exceptions.{ResourceAlreadyExistException, ResourceNotFoundException}
import co.ledger.wallet.daemon.{ErrorCode, ErrorResponseBody}
import co.ledger.wallet.daemon.models._
import co.ledger.wallet.daemon.services.PoolsService
import co.ledger.wallet.daemon.swagger.DocumentedController
import com.twitter.finagle.http.Request
import co.ledger.wallet.daemon.services.AuthenticationService.AuthentifiedUserContext._
import co.ledger.wallet.daemon.services.PoolsService.PoolConfiguration

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.reflect.ClassTag

class WalletPoolsController @Inject()(poolsService: PoolsService) extends DocumentedController {

  get("/pools") {(request: Request) =>
    val modelPools  = for (
      walletPoolsSeq <- poolsService.pools(request.user.get);
      pools = walletPoolsSeq.map { walletPool =>
        for (
          pool <- newInstance(walletPool)
        ) yield pool
      }) yield Future.sequence(pools)
    modelPools.flatten.recover {
      case e: Throwable => {
        error("Internal error", e)
        response.ok()
          .body(ErrorResponseBody(ErrorCode.Internal_Error, "Problem occurred when processing the request, check with developers"))
      }
    }
  }

  get("/pools/:pool_name") {(request: Request) =>
    val poolName = request.getParam("pool_name")
    poolsService.pool(request.user.get, poolName).flatMap(newInstance(_)).recover {
      case pe: ResourceNotFoundException[ClassTag[Pool]] => {
        debug("Not Found", pe)
        response.notFound()
          .body(ErrorResponseBody(ErrorCode.Not_Found, s"$poolName is not a pool"))
      }
      case e: Throwable => {
        error("Internal error", e)
        response.ok()
          .body(ErrorResponseBody(ErrorCode.Internal_Error, "Problem occurred when processing the request, check with developers"))
      }
    }
  }

  post("/pools/:pool_name") { (request: Request) =>
    val poolName = request.getParam("pool_name")
    val modelPool = for (
    // TODO: Deserialize the configuration from the body of the request
      walletPool <- poolsService.createPool(request.user.get, poolName, PoolConfiguration());
      pool = newInstance(walletPool)
    ) yield pool
    modelPool.flatten.recover {
      case alreadyExist: ResourceAlreadyExistException[ClassTag[Pool]] => {
        debug("Duplicate request", alreadyExist)
        response.ok()
          .body(ErrorResponseBody(ErrorCode.Duplicate_Request, s"Attempt creating $poolName request is ignored"))
      }
      case e: Throwable => {
        error("Internal error", e)
        response.ok()
          .body(ErrorResponseBody(ErrorCode.Internal_Error, "Problem occurred when processing the request, check with developers"))
      }
    }
  }

  delete("/pools/:pool_name") {(request: Request) =>
    val poolName = request.getParam("pool_name")
    poolsService.removePool(request.user.get, poolName).recover {
      case pe: ResourceNotFoundException[ClassTag[Pool]] => {
        debug("Not Found", pe)
        response.notFound()
          .body(ErrorResponseBody(ErrorCode.Invalid_Request, s"Attempt deleting $poolName request is ignored"))
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
  case class WalletPoolResult(test: String)
}