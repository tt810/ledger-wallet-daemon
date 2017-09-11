package co.ledger.wallet.daemon.controllers

import javax.inject.Inject

import co.ledger.wallet.daemon.services.WalletsService
import co.ledger.wallet.daemon.swagger.DocumentedController
import co.ledger.wallet.daemon.utils.RichRequest
import com.twitter.finagle.http.Request
import com.twitter.finatra.request.{QueryParam, RouteParam}

class WalletsController @Inject()(walletsService: WalletsService) extends DocumentedController {
  import WalletsController._

  get("/pools/:pool_name/wallets") {(request: GetWalletsRequest) =>
    walletsService.wallets(request.user, request.pool_name, request.offset.getOrElse(0), request.offset.getOrElse(20))
  }

  post("/pools/:pool_name/wallets/:wallet_name") {(request: CreateWalletRequest) =>
    println(s"So you want to create ${request.wallet_name} for ${request.currency_name}")
  }

}

object WalletsController {
  case class GetWalletsRequest(
                              @RouteParam pool_name: String,
                              @QueryParam offset: Option[Int],
                              @QueryParam count: Option[Int],
                              request: Request
                              ) extends RichRequest(request)

  case class CreateWalletRequest(
                                @RouteParam pool_name: String,
                                @RouteParam wallet_name: String,
                                currency_name: String,
                                request: Request
                                )
}