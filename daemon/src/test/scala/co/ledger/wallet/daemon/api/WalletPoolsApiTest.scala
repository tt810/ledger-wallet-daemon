package co.ledger.wallet.daemon.api

import co.ledger.wallet.daemon.models
import co.ledger.wallet.daemon.utils.APIFeatureTest

class WalletPoolsApiTest extends APIFeatureTest {

  test("WalletPools#Create and list single pool") {
    createPool("my_pool")
    val pools = parse[List[models.Pool]](getPools())
    assert(pools.size == 1)
    deletePool("my_pool")
  }

  test("WalletPools#Create and list multiple pool") {
    createPool("your_pool")
    createPool("this_pool")
    val pools = parse[List[models.Pool]](getPools())
    assert(pools.size == 2)
    deletePool("your_pool")
    deletePool("this_pool")
    val pools2 = parse[List[models.Pool]](getPools())
    assert(pools2.size == 0)
  }

  test("WalletPool#Get single pool") {
    createPool("anotha_pool")
    val pool = parse[models.Pool](getPool("anotha_pool"))
    assert(pool.name == "anotha_pool")
    deletePool("anotha_pool")
  }

}
