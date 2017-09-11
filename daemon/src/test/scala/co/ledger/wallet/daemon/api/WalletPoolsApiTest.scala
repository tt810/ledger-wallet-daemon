package co.ledger.wallet.daemon.api

import co.ledger.wallet.daemon.controllers.WalletPoolsController.WalletPoolResult
import co.ledger.wallet.daemon.models
import co.ledger.wallet.daemon.utils.APIFeatureTest
import com.twitter.inject.server.{EmbeddedTwitterServer, FeatureTest}

class WalletPoolsApiTest extends APIFeatureTest {

  test("WalletPools#Create and list single pool") {
    server.httpPost("/pools/my_pool", "", headers = defaultHeaders)
    val pools = parse[List[models.Pool]](server.httpGet("/pools", headers = defaultHeaders))
    server.httpDelete("/pools/my_pool", "", headers = defaultHeaders)
  }

  test("WalletPools#Create and list multiple pool") {
    server.httpPost("/pools/your_pool", "", headers = defaultHeaders)
    server.httpPost("/pools/this_pool", "", headers = defaultHeaders)
    val pools = parse[List[models.Pool]](server.httpGet("/pools", headers = defaultHeaders))
    assert(pools.size == 2)
    server.httpDelete("/pools/your_pool", "", headers = defaultHeaders)
    server.httpDelete("/pools/this_pool", "", headers = defaultHeaders)
    val pools2 = parse[List[models.Pool]](server.httpGet("/pools", headers = defaultHeaders))
    assert(pools2.size == 0)
  }

  test("WalletPool#Get single pool") {
    server.httpPost("/pools/anotha_pool", "", headers = defaultHeaders)
    val pool = parse[models.Pool](server.httpGet("/pools/anotha_pool", headers = defaultHeaders))
    assert(pool.name == "anotha_pool")
    server.httpDelete("/pools/anotha_pool", "", headers = defaultHeaders)
  }

}
