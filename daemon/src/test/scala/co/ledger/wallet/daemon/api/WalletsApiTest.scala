package co.ledger.wallet.daemon.api

import co.ledger.core.Wallet
import co.ledger.wallet.daemon.database.WalletsWithCount
import co.ledger.wallet.daemon.utils.APIFeatureTest
import com.twitter.finagle.http.{Response, Status}

class WalletsApiTest extends APIFeatureTest {

  test("WalletApi#Create then Get same wallet from pool Return OK") {
    val createdW = assertWalletCreation(WALLET_POOL, "my_wallet", "bitcoin", Status.Ok).contentString
    val getW = assertGetWallet(WALLET_POOL, "my_wallet", Status.Ok).contentString

  }

  override def beforeAll(): Unit = {
    super.beforeAll()
    createPool(WALLET_POOL)
  }

  override def afterAll(): Unit = {
    deletePool(WALLET_POOL)
    super.afterAll()
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

  private def assertGetWallets(poolName: String, expected: Status): Response = {
    server.httpGet(path = s"/pools/$poolName/wallets", headers = defaultHeaders, andExpect = expected)
  }
}
