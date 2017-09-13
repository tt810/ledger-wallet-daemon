package co.ledger.wallet.daemon.api

import co.ledger.wallet.daemon.models
import co.ledger.wallet.daemon.models.Pool
import co.ledger.wallet.daemon.utils.APIFeatureTest

class WalletPoolsApiTest extends APIFeatureTest {

  test("WalletPools#Create and list single pool") {
    createPool("my_pool")
    val pools = parse[List[models.Pool]](getPools())
    assert(pools == List(Pool("my_pool", 0)))
    deletePool("my_pool")
  }

  test("WalletPools#Create and list multiple pool") {
    createPool("your_pool")
    createPool("this_pool")
    val pools = parse[List[models.Pool]](getPools())
    assert(pools == List(Pool("your_pool", 0), Pool("this_pool", 0)))
    deletePool("your_pool")
    deletePool("this_pool")
    val pools2 = parse[List[models.Pool]](getPools())
    assert(pools2.size == 0)
  }

  test("WalletPool#Get single pool") {
    createPool("anotha_pool")
    val pool = parse[models.Pool](getPool("anotha_pool"))
    assert(pool == Pool("anotha_pool", 0))
    deletePool("anotha_pool")
  }

  test("WalletPool#Create pool return the created pool") {
    val response = createPool("new_pool")
    val pool = server.mapper.objectMapper.readValue[models.Pool](response.contentString)
    assert(pool == Pool("new_pool", 0))
    deletePool("new_pool")
  }

}
