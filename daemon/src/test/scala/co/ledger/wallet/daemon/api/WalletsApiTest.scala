package co.ledger.wallet.daemon.api

import co.ledger.core.Wallet
import co.ledger.wallet.daemon.database.WalletsWithCount
import co.ledger.wallet.daemon.utils.APIFeatureTest
import com.twitter.finagle.http.{Response, Status}

class WalletsApiTest extends APIFeatureTest {

  test("WalletApi#Create then Get same wallet from pool Return OK") {
    createPool(WALLET_POOL)
    val createdW = assertWalletCreation(WALLET_POOL, "my_wallet", "bitcoin", Status.Ok).contentString
    assert(createdW.contains(""""name":"my_wallet""""))
    assert(createdW.contains(""""wallet_type":"BITCOIN""""))
    val getW = assertGetWallet(WALLET_POOL, "my_wallet", Status.Ok).contentString
    assert(getW.contains(""""name":"my_wallet""""))
    assert(getW.contains(""""wallet_type":"BITCOIN""""))
    deletePool(WALLET_POOL)
  }

  test("WalletApi#Create already exist wallet Return Ok") {
    createPool("duplicate_pool")
    val createdW = assertWalletCreation("duplicate_pool", "my_wallet", "bitcoin", Status.Ok).contentString
    assert(createdW.contains(""""name":"my_wallet""""))
    assert(createdW.contains(""""wallet_type":"BITCOIN""""))
    val createdIgnore = assertWalletCreation("duplicate_pool", "my_wallet", "bitcoin", Status.Ok).contentString
    assert(createdW === createdIgnore)
    deletePool("duplicate_pool")
  }

  test("WalletApi#Get two wallets from pool Return OK") {
    createPool("multi_pool")
    val wallet1 = assertWalletCreation("multi_pool", "wallet_1", "bitcoin", Status.Ok).contentString
    val wallet2 = assertWalletCreation("multi_pool", "wallet_2", "bitcoin", Status.Ok).contentString
    val wallet3 = assertWalletCreation("multi_pool", "wallet_3", "bitcoin", Status.Ok).contentString
    val getW = assertGetWallets("multi_pool", 1, 2, Status.Ok).contentString
    assert(getW.contains(""""count":3"""))
    assert(!getW.contains(""""name":"wallet_1""""))
    assert(getW.contains(""""name":"wallet_2""""))
    assert(getW.contains(""""name":"wallet_3""""))
    deletePool("multi_pool")
  }

  private def walletFromResponse(response: Response): Wallet = server.mapper.objectMapper.readValue[Wallet](response.contentString)
  private def walletsFromResponse(response: Response): WalletsWithCount = server.mapper.objectMapper.readValue[WalletsWithCount](response.contentString)

  private val WALLET_POOL = "wallet_pool"

  private def assertWalletCreation(poolName: String, walletName: String, currencyName: String, expected: Status): Response = {
    server.httpPost(path = s"/pools/$poolName/wallets",
      postBody = s"""{\"currency_name\":\"$currencyName\",\"wallet_name\":\"$walletName\"}""",
      headers = defaultHeaders,
      andExpect = expected)
  }

  private def assertGetWallet(poolName: String, walletName: String, expected: Status): Response = {
    server.httpGet(path = s"/pools/$poolName/wallets/$walletName", headers = defaultHeaders, andExpect = expected)
  }

  private def assertGetWallets(poolName: String, offset: Int, count: Int, expected: Status): Response = {
    server.httpGet(path = s"/pools/$poolName/wallets?offset=$offset&count=$count", headers = defaultHeaders, andExpect = expected)
  }
}
