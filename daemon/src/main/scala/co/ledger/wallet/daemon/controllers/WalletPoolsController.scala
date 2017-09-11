package co.ledger.wallet.daemon.controllers

import javax.inject.Inject

import co.ledger.wallet.daemon.converters.PoolConverter
import co.ledger.wallet.daemon.models
import co.ledger.wallet.daemon.services.PoolsService
import co.ledger.wallet.daemon.swagger.DocumentedController
import com.twitter.finagle.http.Request
import com.twitter.util.Future
import co.ledger.wallet.daemon.utils._
import co.ledger.wallet.daemon.services.AuthenticationService.AuthentifiedUserContext._
import co.ledger.wallet.daemon.services.PoolsService.PoolConfiguration
import com.twitter.finatra.request.RouteParam

import scala.concurrent.ExecutionContext.Implicits.global

class WalletPoolsController @Inject()(poolsService: PoolsService, poolConverter: PoolConverter) extends DocumentedController {
  import WalletPoolsController._

  get("/pools") {(request: Request) =>
    poolsService.pools(request.user.get).asTwitter().flatMap({(pools) =>
      val f: Seq[Future[models.Pool]] = pools map {(pool) =>
        poolConverter(pool)
      }
      Future.collect(f.toList)
    })
  }

  get("/pools/:pool_name") {(request: Request) =>
    val poolName = request.getParam("pool_name")
    poolsService.pool(request.user.get, poolName).asTwitter().flatMap(poolConverter.apply)
  }

  post("/pools/:pool_name") {(request: Request) =>
    val poolName = request.getParam("pool_name")
    val configuration = PoolConfiguration() // TODO: Deserialize the configuration from the body of the request
    poolsService.createPool(request.user.get, poolName, PoolConfiguration()).asTwitter().flatMap({(p) =>
      poolConverter(p)
    })
  }

  delete("/pools/:pool_name") {(request: Request) =>
    val poolName = request.getParam("pool_name")
    poolsService.removePool(request.user.get, poolName)
  }

}

object WalletPoolsController {
  case class WalletPoolResult(test: String)
}