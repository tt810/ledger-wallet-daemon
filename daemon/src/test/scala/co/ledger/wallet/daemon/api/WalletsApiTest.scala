package co.ledger.wallet.daemon.api

import co.ledger.core.Wallet
import co.ledger.wallet.daemon.models
import co.ledger.wallet.daemon.models.WalletPool
import co.ledger.wallet.daemon.services.WalletsService.WalletBulk
import co.ledger.wallet.daemon.utils.APIFeatureTest
import com.twitter.finagle.http.{Response, Status}
import org.scalatest.Ignore
@Ignore
class WalletsApiTest extends APIFeatureTest {

  test("WalletApi#Create then Get same wallet from pool Return OK") {
    val wallet: Wallet = walletFromResponse(assertWalletCreation(WALLET_POOL, "my_wallet", "bitcoin", Status.Ok))
    val getR = assertGetWallet(WALLET_POOL, "my_wallet", Status.Ok)
    assert(wallet == walletFromResponse(getR))
  }

  test("WalletApi#Get wallets from pool Return OK") {
    val wallet1 = walletFromResponse(assertWalletCreation(WALLET_POOL, "wallet_1", "bitcoin", Status.Ok))
    val wallet2 = walletFromResponse(assertWalletCreation(WALLET_POOL, "wallet_2", "bitcoin", Status.Ok))
    assert(WalletBulk(2, Array(wallet1, wallet2)) == walletsFromResponse(assertGetWallets(WALLET_POOL, Status.Ok)))
    val pool = parse[models.WalletPool](getPool(WALLET_POOL))
    assert(pool == WalletPool(WALLET_POOL, 2))
  }

  test("WalletApi#No wallet in the pool") {
    assertGetWallets(WALLET_POOL, Status.Ok)
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
  private def walletsFromResponse(response: Response): WalletBulk = server.mapper.objectMapper.readValue[WalletBulk](response.contentString)

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
