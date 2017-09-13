package co.ledger.wallet.daemon.controllers

import javax.inject.Inject

import co.ledger.wallet.daemon.models._
import co.ledger.wallet.daemon.services.PoolsService
import co.ledger.wallet.daemon.swagger.DocumentedController
import com.twitter.finagle.http.Request
import co.ledger.wallet.daemon.services.AuthenticationService.AuthentifiedUserContext._
import co.ledger.wallet.daemon.services.PoolsService.PoolConfiguration

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class WalletPoolsController @Inject()(poolsService: PoolsService) extends DocumentedController {

  get("/pools") {(request: Request) =>
    val modelPools  = for (
      walletPoolsSeq <- poolsService.pools(request.user.get);
      pools = walletPoolsSeq.map { walletPool =>
        for (
          pool <- newInstance(walletPool)
        ) yield pool
      }) yield Future.sequence(pools)
    modelPools.flatten
  }

  get("/pools/:pool_name") {(request: Request) =>
    val poolName = request.getParam("pool_name")
    poolsService.pool(request.user.get, poolName).flatMap(newInstance(_))
  }

  post("/pools/:pool_name") {(request: Request) =>
    val poolName = request.getParam("pool_name")
    val configuration = PoolConfiguration() // TODO: Deserialize the configuration from the body of the request
    val modelPool = for (
      walletPool <- poolsService.createPool(request.user.get, poolName, configuration);
      pool = newInstance(walletPool)
    ) yield pool
    modelPool.flatten
  }

  delete("/pools/:pool_name") {(request: Request) =>
    val poolName = request.getParam("pool_name")
    poolsService.removePool(request.user.get, poolName)
  }

}

object WalletPoolsController {
  case class WalletPoolResult(test: String)
}