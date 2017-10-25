package co.ledger.wallet.daemon.controllers

import javax.inject.Inject

import co.ledger.wallet.daemon.async.MDCPropagatingExecutionContext
import co.ledger.wallet.daemon.controllers.requests.{CommonMethodValidations, RichRequest}
import co.ledger.wallet.daemon.controllers.responses.ResponseSerializer
import co.ledger.wallet.daemon.services.AuthenticationService.AuthentifiedUserContext._
import co.ledger.wallet.daemon.services.PoolsService.PoolConfiguration
import co.ledger.wallet.daemon.services.{LogMsgMaker, PoolsService}
import com.fasterxml.jackson.annotation.JsonProperty
import com.twitter.finagle.http.Request
import com.twitter.finatra.http.Controller
import com.twitter.finatra.request.RouteParam
import com.twitter.finatra.validation.{MethodValidation, NotEmpty, ValidationResult}

import scala.concurrent.ExecutionContext

class WalletPoolsController @Inject()(poolsService: PoolsService) extends Controller {
  implicit val ec: ExecutionContext = MDCPropagatingExecutionContext.Implicits.global
  import WalletPoolsController._

  get("/pools") {(request: Request) =>
    info(LogMsgMaker.newInstance("GET wallet pools request")
      .append("request", request)
      .toString())
    poolsService.pools(request.user.get).recover {
      case e: Throwable => responseSerializer.serializeInternalError(response, e)
    }
  }

  get("/pools/:pool_name") {(request: PoolRouteRequest) =>
    info(LogMsgMaker.newInstance("GET wallet pool request")
      .append("request", request)
      .append("pool_name", request.pool_name)
      .toString())
    poolsService.pool(request.user, request.pool_name).map {
      case Some(view) => responseSerializer.serializeOk(view, response)
      case None => responseSerializer.serializeNotFound(
        Map("response" -> "Wallet pool doesn't exist", "pool_name" -> request.pool_name), response)
    }.recover {
      case e: Throwable => responseSerializer.serializeInternalError(response, e)
    }
  }

  post("/pools/operations/synchronize") { request: Request =>
    implicit val ec: ExecutionContext = MDCPropagatingExecutionContext.cachedNamedThreads("end-point-synchronization-thread-pool")
    info(LogMsgMaker.newInstance("SYNC wallet pools request")
      .append("request", request)
      .toString())
    val t0 = System.currentTimeMillis()
    poolsService.syncOperations().map { result =>
      val t1 = System.currentTimeMillis()
      info(s"Synchronization finished, elapsed time: ${(t1 - t0)} milliseconds")
      result
    }.recover {
      case e: Throwable => responseSerializer.serializeInternalError(response, e)
    }
  }

  post("/pools") { (request: CreationRequest) =>
    info(LogMsgMaker.newInstance("CREATE wallet pool request")
      .append("request", request.request)
      .toString())
    val poolName = request.pool_name
    // TODO: Deserialize the configuration from the body of the request
    poolsService.createPool(request.user, poolName, PoolConfiguration()).recover {
      case e: Throwable => responseSerializer.serializeInternalError(response, e)
    }
  }

  delete("/pools/:pool_name") {(request: Request) =>
    val poolName = request.getParam("pool_name")
    info(LogMsgMaker.newInstance("DELETE wallet pool request")
      .append("request", request)
      .append("pool_name", poolName)
      .toString())
    poolsService.removePool(request.user.get, poolName).recover {
      case e: Throwable => responseSerializer.serializeInternalError(response, e)
    }
  }

  private val responseSerializer: ResponseSerializer = ResponseSerializer.newInstance
}

object WalletPoolsController {
  case class CreationRequest(
                            @NotEmpty @JsonProperty pool_name: String,
                            request: Request
                            ) extends RichRequest(request) {
    @MethodValidation
    def validatePoolName: ValidationResult = CommonMethodValidations.validateName("pool_name", pool_name)

  }

  case class PoolRouteRequest(
                               @RouteParam pool_name: String,
                               request: Request) extends RichRequest(request) {
    @MethodValidation
    def validatePoolName: ValidationResult = CommonMethodValidations.validateName("pool_name", pool_name)
  }

}