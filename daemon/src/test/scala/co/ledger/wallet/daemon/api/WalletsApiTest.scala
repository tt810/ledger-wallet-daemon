package co.ledger.wallet.daemon.api

import co.ledger.wallet.daemon.models
import co.ledger.wallet.daemon.utils.APIFeatureTest

class WalletsApiTest extends APIFeatureTest {

  test("WalletApi#Get wallets from empty pool") {
    createPool("toto")
  }

  test("WalletApi#Create wallet from empty pool") {

  }

}
