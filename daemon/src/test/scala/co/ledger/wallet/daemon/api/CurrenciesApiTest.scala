package co.ledger.wallet.daemon.api

import co.ledger.wallet.daemon.models.{Currency, BitcoinLikeNetworkParams}
import co.ledger.wallet.daemon.utils.APIFeatureTest
import com.twitter.finagle.http.{Response, Status}

class CurrenciesApiTest extends APIFeatureTest {

  test("CurrenciesApi#Get currency with given pool name and currency name returns OK") {
    val response: Response = assertCurrency(CURRENCY_POOL, CURRENCY_BTC, Status.Ok)
    val currency: Currency = server.mapper.objectMapper.readValue[Currency](response.contentString)
    assert(currency == EXPECTED_BTC_CURRENCY)
  }

  test("CurrenciesApi#Get currency from non-existing pool returns bad request") {
    assertCurrency(CURRENCY_NON_EXIST_POOL, CURRENCY_BTC, Status.BadRequest)
  }

  test("CurrenciesApi#Get non-supported currency from existing pool returns currency not found") {
    assertCurrency(CURRENCY_POOL, CURRENCY_NON_EXIST, Status.NotFound)
  }

  test("CurrenciesApi#Get currencies returns all") {
    val response: Response = assertCurrencies(CURRENCY_POOL, Status.Ok)
    val currencies: List[Currency] = server.mapper.objectMapper.readValue[List[Currency]](response.contentString)
    assert(currencies == List(EXPECTED_BTC_CURRENCY))
  }

  test("CurrenciesApi#Get currencies from non-existing pool returns bad request") {
    assertCurrencies(CURRENCY_NON_EXIST_POOL, Status.BadRequest)
  }

  private def assertCurrency(poolName: String, currencyName: String, expected: Status): Response = {
    server.httpGet(s"/pools/$poolName/currencies/$currencyName", headers = defaultHeaders, andExpect = expected)
  }

  private def assertCurrencies(poolName: String, expected: Status): Response = {
    server.httpGet(s"/pools/$poolName/currencies", headers = defaultHeaders, andExpect = expected)
  }

  override def beforeAll(): Unit = {
    super.beforeAll()
    createPool(CURRENCY_POOL)
  }

  override def afterAll(): Unit = {
    deletePool(CURRENCY_POOL)
    super.afterAll()
  }

  private val CURRENCY_POOL = "currency_pool"
  private val CURRENCY_BTC = "bitcoin"
  private val CURRENCY_NON_EXIST = "ethereum"
  private val CURRENCY_NON_EXIST_POOL = "non_exist_pool"
  private val EXPECTED_BTC_CURRENCY = Currency(
    "bitcoin",
    "BITCOIN",
    0,
    "bitcoin",
    List(
      co.ledger.wallet.daemon.models.Unit("satoshi","satoshi","satoshi",0),
      co.ledger.wallet.daemon.models.Unit("bitcoin", "BTC", "BTC", 8),
      co.ledger.wallet.daemon.models.Unit("milli-bitcoin","mBTC", "mBTC", 5),
      co.ledger.wallet.daemon.models.Unit("micro-bitcoin", "μBTC", "μBTC", 2),
      ),
    BitcoinLikeNetworkParams("btc", "00", "05", "0488B21E", "PER_BYTE", 5430, "Bitcoin signed message:\n", false)
  )

}

