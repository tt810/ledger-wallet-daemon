package co.ledger.wallet.daemon.api

import co.ledger.core.Wallet
import co.ledger.wallet.daemon.{ErrorCode, ErrorResponseBody}
import co.ledger.wallet.daemon.database.WalletsWithCount
import co.ledger.wallet.daemon.utils.APIFeatureTest
import com.twitter.finagle.http.{Response, Status}

class WalletsApiTest extends APIFeatureTest {

  test("WalletsApi#Create then Get same wallet from pool Return OK") {
    createPool(WALLET_POOL)
    val createdW = assertWalletCreation(WALLET_POOL, "my_wallet", "bitcoin", Status.Ok).contentString
    assert(createdW.contains(""""name":"my_wallet""""))
    assert(createdW.contains(""""wallet_type":"BITCOIN""""))
    val getW = assertGetWallet(WALLET_POOL, "my_wallet", Status.Ok).contentString
    assert(getW.contains(""""name":"my_wallet""""))
    assert(getW.contains(""""wallet_type":"BITCOIN""""))
    deletePool(WALLET_POOL)
  }

  test("WalletsApi#Get non exist wallet from existing pool Return Not Found") {
    createPool(WALLET_POOL)
    val notFoundErr = server.mapper.objectMapper.readValue[ErrorResponseBody](
      assertGetWallet(WALLET_POOL, "not_exist_wallet", Status.NotFound).contentString)
    assert(notFoundErr === ErrorResponseBody(ErrorCode.Not_Found, s"Wallet not_exist_wallet doesn't exist"))
  }

  test("WalletsApi#Create already exist wallet Return Ok") {
    createPool("duplicate_pool")
    val createdW = assertWalletCreation("duplicate_pool", "duplicate_wallet", "bitcoin", Status.Ok).contentString
    assert(createdW.contains(""""name":"duplicate_wallet""""))
    assert(createdW.contains(""""wallet_type":"BITCOIN""""))
    val createdIgnore = assertWalletCreation("duplicate_pool", "duplicate_wallet", "bitcoin", Status.Ok).contentString
    assert(createdW === createdIgnore)
    deletePool("duplicate_pool")
  }

  test("WalletsApi#Get two wallets from pool Return OK") {
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

  test("WalletsApi#Get no wallets from existing pool") {
    createPool("empty_pool")
    val result = assertGetWallets("empty_pool", 0, 2, Status.Ok)
    assert(WalletsWithCount(0, Array[Wallet]()) === walletsFromResponse(result))
  }

  test("WalletsApi#Get/Post wallet(s) from non existing pool") {
    val expectedErr = ErrorResponseBody(ErrorCode.Invalid_Request, "Wallet pool not_existing_pool doesn't exist")
    val result = assertGetWallets("not_existing_pool", 0, 2, Status.BadRequest)
    val getWalletsErr = server.mapper.objectMapper.readValue[ErrorResponseBody](result.contentString)
    assert(getWalletsErr === expectedErr)
    val getWalletErr = server.mapper.objectMapper.readValue[ErrorResponseBody](
      assertGetWallet("not_existing_pool", "my_wallet", Status.BadRequest).contentString)
    assert(getWalletErr === expectedErr)
    val postWalletErr = server.mapper.objectMapper.readValue[ErrorResponseBody](
      assertWalletCreation("not_existing_pool", "my_wallet", "bitcoin", Status.BadRequest).contentString)
    assert(postWalletErr === expectedErr)
  }

  test("WalletsApi#Post wallet with non exist currency to existing pool") {
    createPool(WALLET_POOL)
    val expectedErr = ErrorResponseBody(ErrorCode.Invalid_Request, "Currency non_existing_currency is not supported")
    val result = assertWalletCreation(WALLET_POOL, "my_wallet", "non_existing_currency", Status.BadRequest)
    val postWalletErr = server.mapper.objectMapper.readValue[ErrorResponseBody](result.contentString)
    assert(expectedErr === postWalletErr)
    deletePool(WALLET_POOL)
  }

  private def walletFromResponse(response: Response): Wallet = server.mapper.objectMapper.readValue[Wallet](response.contentString)
  private def walletsFromResponse(response: Response): WalletsWithCount = server.mapper.objectMapper.readValue[WalletsWithCount](response.contentString)

  private val WALLET_POOL = "wallet_pool"

  private def assertGetWallet(poolName: String, walletName: String, expected: Status): Response = {
    server.httpGet(path = s"/pools/$poolName/wallets/$walletName", headers = defaultHeaders, andExpect = expected)
  }

  private def assertGetWallets(poolName: String, offset: Int, count: Int, expected: Status): Response = {
    server.httpGet(path = s"/pools/$poolName/wallets?offset=$offset&count=$count", headers = defaultHeaders, andExpect = expected)
  }
}
