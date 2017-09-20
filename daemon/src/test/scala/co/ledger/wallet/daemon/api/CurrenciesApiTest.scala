package co.ledger.wallet.daemon.api

import co.ledger.wallet.daemon.{ErrorCode, ErrorResponseBody}
import co.ledger.wallet.daemon.models.{BitcoinLikeNetworkParams, Currency, Unit => CurrencyUnit}
import co.ledger.wallet.daemon.utils.APIFeatureTest
import com.twitter.finagle.http.{Response, Status}
import org.scalatest.Ignore
@Ignore
class CurrenciesApiTest extends APIFeatureTest {

  test("CurrenciesApi#Get currency with given pool name and currency name returns OK") {
    val response: Response = assertCurrency(CURRENCY_POOL, CURRENCY_BTC, Status.Ok)
    val currency: Currency = server.mapper.objectMapper.readValue[Currency](response.contentString)
    assert(currency == EXPECTED_BTC_CURRENCY)
  }

  test("CurrenciesApi#Get currency from non-existing pool returns bad request") {
    assert(server.mapper.objectMapper.readValue[ErrorResponseBody](
      assertCurrency(CURRENCY_NON_EXIST_POOL, CURRENCY_BTC, Status.BadRequest).contentString)
      == ErrorResponseBody(ErrorCode.Invalid_Request,"non_exist_pool doesn't exist"))
  }

  test("CurrenciesApi#Get non-supported currency from existing pool returns currency not found") {
    assert(server.mapper.objectMapper.readValue[ErrorResponseBody](
    assertCurrency(CURRENCY_POOL, CURRENCY_NON_EXIST, Status.NotFound).contentString)
      == ErrorResponseBody(ErrorCode.Not_Found, s"Currency $CURRENCY_NON_EXIST is not supported"))
  }

  test("CurrenciesApi#Get currencies returns all") {
    val response: Response = assertCurrencies(CURRENCY_POOL, Status.Ok)
    val currencies: List[Currency] = server.mapper.objectMapper.readValue[List[Currency]](response.contentString)
    assert(currencies == List(EXPECTED_BTC_CURRENCY))
  }

  test("CurrenciesApi#Get currencies from non-existing pool returns bad request") {
    assert(server.mapper.objectMapper.readValue[ErrorResponseBody](
      assertCurrencies(CURRENCY_NON_EXIST_POOL, Status.BadRequest).contentString)
      == ErrorResponseBody(ErrorCode.Invalid_Request,"non_exist_pool doesn't exist"))
  }

  test("CurrenciesApi#Post currency from non-existing pool returns bad request") {
    assert(server.mapper.objectMapper.readValue[ErrorResponseBody](
      assertPost(CURRENCY_NON_EXIST_POOL, Status.BadRequest).contentString)
      == ErrorResponseBody(ErrorCode.Invalid_Request,"non_exist_pool doesn't exist"))
  }

  test("CurrenciesApi#Post currency to existing pool return ok") {
    assert(server.mapper.objectMapper.readValue[Currency](
      assertPost(CURRENCY_POOL, Status.Ok).contentString)
      == server.mapper.objectMapper.readValue[Currency](CURRENCY_TO_BE_ADDED))
  }

  test("CurrenciesApi#Delete currency from non-existing pool returns bad request") {
    assert(server.mapper.objectMapper.readValue[ErrorResponseBody](
      assertDelete(CURRENCY_NON_EXIST_POOL, CURRENCY_BTC, Status.BadRequest).contentString)
      == ErrorResponseBody(ErrorCode.Invalid_Request,"non_exist_pool doesn't exist"))
  }

  test("CurrenciesApi#Delete currency not exist returns ok") {
    assert(server.mapper.objectMapper.readValue[ErrorResponseBody](
      assertDelete(CURRENCY_POOL, CURRENCY_NON_EXIST, Status.Ok).contentString)
      == ErrorResponseBody(ErrorCode.Not_Found,"Currency ethereum is not supported"))
  }

  private def assertDelete(poolName: String, currencyName: String, expected: Status): Response = {
    server.httpDelete(s"/pools/$poolName/currencies/$currencyName", headers = defaultHeaders, andExpect = expected)
  }

  private def assertPost(poolName: String, expected: Status): Response = {
    server.httpPost(s"/pools/$poolName/currencies", CURRENCY_TO_BE_ADDED, headers = defaultHeaders, andExpect = expected)
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
      CurrencyUnit("satoshi","satoshi","satoshi",0),
      CurrencyUnit("bitcoin", "BTC", "BTC", 8),
      CurrencyUnit("milli-bitcoin","mBTC", "mBTC", 5),
      CurrencyUnit("micro-bitcoin", "μBTC", "μBTC", 2),
      ),
    BitcoinLikeNetworkParams("btc", "00", "05", "0488B21E", "PER_BYTE", 5430, "Bitcoin signed message:\n", false)
  )

  private val CURRENCY_TO_BE_ADDED =
    """{"name" : "ethereum",""" +
      """"family" : "ETHEREUM",""" +
      """"bip_44_coin_type" : 0,""" +
      """"payment_uri_scheme" : "ethereum",""" +
      """"units" : [""" +
      """{""" +
      """"name" : "wei",""" +
      """"symbol" : "wei",""" +
      """"code" : "wei",""" +
      """"magnitude" : 0""" +
      """},""" +
      """{""" +
      """"name" : "ethereum",""" +
      """"symbol" : "ETH",""" +
      """"code" : "ETH",""" +
      """"magnitude" : 8""" +
      """}""" +
      """],""" +
      """"network_params" : {""" +
      """"identifier" : "eth",""" +
      """"p2pkh_version" : "00",""" +
      """"p2sh_version" : "05",""" +
      """"xpub_version" : "0488B21E",""" +
      """"fee_policy" : "PER_BYTE",""" +
      """"dust_amount" : 5430,""" +
      """"message_prefix" : "Ethereum signed message:n",""" +
      """"uses_timestamped_transaction" : false""" +
      """}""" +
      """}"""

}

