package co.ledger.wallet.daemon.controllers

import javax.inject.Inject

import co.ledger.wallet.daemon.async.MDCPropagatingExecutionContext
import co.ledger.wallet.daemon.controllers.requests.{CommonMethodValidations, RichRequest}
import co.ledger.wallet.daemon.controllers.responses.ResponseSerializer
import co.ledger.wallet.daemon.exceptions._
import co.ledger.wallet.daemon.services.{LogMsgMaker, PoolsService}
import com.twitter.finagle.http.Request
import co.ledger.wallet.daemon.services.AuthenticationService.AuthentifiedUserContext._
import co.ledger.wallet.daemon.services.PoolsService.PoolConfiguration
import com.fasterxml.jackson.annotation.JsonProperty
import com.twitter.finatra.http.Controller
import com.twitter.finatra.request.RouteParam
import com.twitter.finatra.validation.{MethodValidation, NotEmpty}

import scala.concurrent.ExecutionContext

class WalletPoolsController @Inject()(poolsService: PoolsService) extends Controller {
  implicit val ec: ExecutionContext = MDCPropagatingExecutionContext.Implicits.global
  import WalletPoolsController._

  get("/pools") {(request: Request) =>
    info(LogMsgMaker.newInstance("GET wallet pools request")
      .append("request", request)
      .toString())
    poolsService.pools(request.user.get).recover {
      case e: Throwable => responseSerializer.serializeInternalErrorToOk(response, e)
    }
  }

  get("/pools/:pool_name") {(request: PoolRouteRequest) =>
    info(LogMsgMaker.newInstance("GET wallet pool request")
      .append("request", request)
      .append("pool_name", request.pool_name)
      .toString())
    poolsService.pool(request.user, request.pool_name).recover {
      case pe: WalletPoolNotFoundException => responseSerializer.serializeNotFound(
        Map("response"->"Wallet pool doesn't exist", "pool_name" -> request.pool_name),
        response,
        pe)
      case e: Throwable => responseSerializer.serializeInternalErrorToOk(response, e)
    }
  }

  post("/pools") { (request: CreationRequest) =>
    info(LogMsgMaker.newInstance("CREATE wallet pool request")
      .append("request", request.request)
      .toString())
    val poolName = request.pool_name
    // TODO: Deserialize the configuration from the body of the request
    poolsService.createPool(request.user, poolName, PoolConfiguration()).recover {
      case e: Throwable => responseSerializer.serializeInternalErrorToOk(response, e)
    }
  }

  delete("/pools/:pool_name") {(request: Request) =>
    val poolName = request.getParam("pool_name")
    info(LogMsgMaker.newInstance("DELETE wallet pool request")
      .append("request", request)
      .append("pool_name", poolName)
      .toString())
    poolsService.removePool(request.user.get, poolName).recover {
      case pe: WalletPoolNotFoundException => responseSerializer.serializeNotFound(
        Map("response" -> "Wallet pool doesn't exist", "pool_name" -> poolName),
        response,
        pe)
      case e: Throwable => responseSerializer.serializeInternalErrorToOk(response, e)
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
    def validatePoolName = CommonMethodValidations.validateName("pool_name", pool_name)

  }

  case class PoolRouteRequest(
                               @RouteParam pool_name: String,
                               request: Request) extends RichRequest(request) {
    @MethodValidation
    def validatePoolName = CommonMethodValidations.validateName("pool_name", pool_name)
  }

}