package co.ledger.wallet.daemon.api

import co.ledger.wallet.daemon.controllers.WalletPoolsController.WalletPoolResult
import co.ledger.wallet.daemon.models
import co.ledger.wallet.daemon.utils.APIFeatureTest
import com.twitter.inject.server.{EmbeddedTwitterServer, FeatureTest}

class WalletPoolsApiTest extends APIFeatureTest {

  test("WalletPools#Create and list single pool") {
    server.httpPost("/pools/my_pool/create", "", headers = defaultHeaders)
    val pools = parse[Seq[models.Pool]](server.httpGet("/pools", headers = defaultHeaders))
  }

}
