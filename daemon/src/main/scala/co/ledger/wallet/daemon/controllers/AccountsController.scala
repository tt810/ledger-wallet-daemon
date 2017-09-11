package co.ledger.wallet.daemon.controllers

import javax.inject.Inject

import co.ledger.wallet.daemon.controllers.WalletsController.GetWalletsRequest
import co.ledger.wallet.daemon.services.AccountsService
import co.ledger.wallet.daemon.swagger.DocumentedController

class AccountsController @Inject()(accountsService: AccountsService) extends DocumentedController {

  get("/pools/:pool_name/wallets/:wallet_name/next_account") {(request: GetWalletsRequest) =>
    "Hello World"
  }

}

object AccountsController {

}