package co.ledger.wallet.daemon.api

import co.ledger.wallet.daemon.models.Account
import co.ledger.wallet.daemon.utils.APIFeatureTest
import com.twitter.finagle.http.{Response, Status}
import org.scalatest.Ignore
@Ignore
class AccountsApiTest extends APIFeatureTest {

  test("AccountsApi#Get empty accounts") {
    createPool("account_pool")
    assertWalletCreation("account_pool", "account_wallet", "bitcoin", Status.Ok)
    val result = assertGetAccounts(None, "account_pool", "account_wallet", Status.BadRequest)
    val expectedResponse = Seq[Account]()
    assert(expectedResponse === server.mapper.objectMapper.readValue[Seq[Account]](result.contentString))
    deletePool("account_pool")
  }

  test("AccountsApi#Get account with index from empty wallet") {
    createPool("account_pool")
    assertWalletCreation("account_pool", "individual_account_wallet", "bitcoin", Status.Ok)
    val individual = assertGetAccounts(Option(1), "account_pool", "individual_account_wallet", Status.BadRequest)

    deletePool("account_pool")
  }

  test("AccountsApi#Create account") {
    createPool("account_pool")
    assertWalletCreation("account_pool", "accounts_wallet", "bitcoin", Status.Ok)
    assertCreateAccount(CORRECT_BODY, "account_pool", "accounts_wallet", Status.Ok)
    deletePool("account_pool")
  }

  test("AccountsApi#Create account with no pubkey") {
    createPool("account_pool")
    assertWalletCreation("account_pool", "accounts_wallet", "bitcoin", Status.Ok)
    assertCreateAccount(MISSING_PUBKEY_BODY, "account_pool", "accounts_wallet", Status.BadRequest)
    deletePool("account_pool")
  }

  test("AccountsApi#Create account with invalid request body") {
    createPool("account_pool")
    assertWalletCreation("account_pool", "accounts_wallet", "bitcoin", Status.Ok)
    assertCreateAccount(INVALID_BODY, "account_pool", "accounts_wallet", Status.BadRequest)
    deletePool("account_pool")
  }

  test("AccountsApi#Create account with request body as invalid json") {
    createPool("account_pool")
    assertWalletCreation("account_pool", "accounts_wallet", "bitcoin", Status.Ok)
    assertCreateAccount(INVALID_JSON, "account_pool", "accounts_wallet", Status.BadRequest)
    deletePool("account_pool")
  }

  test("AccountsApi#Get accounts same as get individual account") {

  }

  test("AccountsApi#Get account(s) from non exist pool return bad request") {

  }

  test("AccountsApi#Get account(s) from non exist wallet return bad request") {

  }

  private def assertGetAccounts(index: Option[Int], poolName: String, walletName: String, expected: Status): Response = {
    index match {
      case None => server.httpGet(s"/pools/$poolName/wallets/$walletName/accounts", headers = defaultHeaders, andExpect = expected)
      case Some(i) => server.httpGet(s"/pools/$poolName/wallets/$walletName/accounts/$i", headers = defaultHeaders, andExpect = expected)
    }
  }

  private def assertCreateAccount(accountCreationBody: String, poolName: String, walletName: String, expected: Status): Response = {
    server.httpPost(s"/pools/$poolName/wallets/$walletName/accounts", accountCreationBody, headers = defaultHeaders, andExpect = expected)
  }

  private val CORRECT_BODY =
    """{""" +
      """"account_index": 0,""" +
      """"derivations": [""" +
      """{""" +
      """"path": "path to brightness",""" +
      """"owner": "whoever owns",""" +
      """"pub_key": "03B4A94D8E33308DD08A3A8C937822101E229D85A2C0DFABC236A8C6A82E58076D",""" +
      """"chain_code": "234A94D8E33308DD08A3A8C937822"""" +
      """},""" +
      """{""" +
      """"path": "path to darkness",""" +
      """"owner": "nobody owns",""" +
      """"pub_key": "03B4A94D8E33308DD08A3A8C937822101E229D85A2C0DFABC236A8C6A82E58076D",""" +
      """"chain_code": "D85A2C0DFABC236A8C6A82E58076D"""" +
      """}""" +
      """]""" +
      """}"""
  private val MISSING_PUBKEY_BODY =
    """{""" +
      """"account_index": 0,""" +
      """"derivations": [""" +
      """{""" +
      """"path": "path to brightness",""" +
      """"owner": "whoever owns",""" +
      """"chain_code": "234A94D8E33308DD08A3A8C937822"""" +
      """},""" +
      """{""" +
      """"path": "path to darkness",""" +
      """"owner": "nobody owns",""" +
      """"pub_key": "03B4A94D8E33308DD08A3A8C937822101E229D85A2C0DFABC236A8C6A82E58076D",""" +
      """"chain_code": "D85A2C0DFABC236A8C6A82E58076D"""" +
      """}""" +
      """]""" +
      """}"""
  private val INVALID_BODY =
    """{""" +
      """"account_index": 0,""" +
      """"derivations": [""" +
      """{""" +
      """"owner": "whoever owns",""" +
      """"pub_key": "03B4A94D8E33308DD08A3A8C937822101E229D85A2C0DFABC236A8C6A82E58076D",""" +
      """"chain_code": "234A94D8E33308DD08A3A8C937822"""" +
      """},""" +
      """{""" +
      """"path": "path to darkness",""" +
      """"owner": "nobody owns",""" +
      """"pub_key": "03B4A94D8E33308DD08A3A8C937822101E229D85A2C0DFABC236A8C6A82E58076D",""" +
      """"chain_code": "D85A2C0DFABC236A8C6A82E58076D"""" +
      """}""" +
      """]""" +
      """}"""
  private val INVALID_JSON =
    """{""" +
      """"account_index": 0,""" +
      """"derivations": [""" +
      """{""" +
      """"path": "path to brightness",""" +
      """"owner": "whoever owns",""" +
      """"pub_key": "03B4A94D8E33308DD08A3A8C937822101E229D85A2C0DFABC236A8C6A82E58076D",""" +
      """"chain_code": "234A94D8E33308DD08A3A8C937822"""" +
      """},""" +
      """{""" +
      """"path": "path to darkness",""" +
      """"owner": "nobody owns",""" +
      """"pub_key": "03B4A94D8E33308DD08A3A8C937822101E229D85A2C0DFABC236A8C6A82E58076D",""" +
      """"chain_code": "D85A2C0DFABC236A8C6A82E58076D"""" +
      """}""" +
      """]""" +
      """"""
}
