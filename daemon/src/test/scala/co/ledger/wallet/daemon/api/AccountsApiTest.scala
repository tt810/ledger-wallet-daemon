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
    assertCreateAccount(MISSING_PATH_BODY, "account_pool", "accounts_wallet", Status.BadRequest)
    deletePool("account_pool")
  }

  test("AccountsApi#Create account fail core lib validation") {
    createPool("account_pool")
    assertWalletCreation("account_pool", "accounts_wallet", "bitcoin", Status.Ok)
    assertCreateAccount(INVALID_ARGUMENT_BODY, "account_pool", "accounts_wallet", Status.BadRequest)
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
      """"owner": "main",""" +
      """"path": "44'/0'/0'",""" +
      """"pub_key": "0437bc83a377ea025e53eafcd18f299268d1cecae89b4f15401926a0f8b006c0f7ee1b995047b3e15959c5d10dd1563e22a2e6e4be9572aa7078e32f317677a901",""" +
      """"chain_code": "d1bb833ecd3beed6ec5f6aa79d3a424d53f5b99147b21dbc00456b05bc978a71"""" +
      """},""" +
      """{""" +
      """"owner": "main",""" +
      """"path": "44'/0'",""" +
      """"pub_key": "04fb60043afe80ee1aeb0160e2aafc94690fb4427343e8d4bf410105b1121f7a44a311668fa80a7a341554a4ef5262bc6ebd8cc981b8b600dafd40f7682edb5b3b",""" +
      """"chain_code": "88c2281acd51737c912af74cc1d1a8ba564eb7925e0d58a5500b004ba76099cb"""" +
      """}""" +
      """]""" +
      """}"""
  private val INVALID_ARGUMENT_BODY =
    """{""" +
      """"account_index": 0,""" +
      """"derivations": [""" +
      """{""" +
      """"owner": "different than next owner",""" +
      """"path": "44'/0'/0'",""" +
      """"pub_key": "0437bc83a377ea025e53eafcd18f299268d1cecae89b4f15401926a0f8b006c0f7ee1b995047b3e15959c5d10dd1563e22a2e6e4be9572aa7078e32f317677a901",""" +
      """"chain_code": "d1bb833ecd3beed6ec5f6aa79d3a424d53f5b99147b21dbc00456b05bc978a71"""" +
      """},""" +
      """{""" +
      """"owner": "main",""" +
      """"path": "44'/0'",""" +
      """"pub_key": "04fb60043afe80ee1aeb0160e2aafc94690fb4427343e8d4bf410105b1121f7a44a311668fa80a7a341554a4ef5262bc6ebd8cc981b8b600dafd40f7682edb5b3b",""" +
      """"chain_code": "88c2281acd51737c912af74cc1d1a8ba564eb7925e0d58a5500b004ba76099cb"""" +
      """}""" +
      """]""" +
      """}"""
  private val MISSING_PUBKEY_BODY =
    """{""" +
      """"account_index": 0,""" +
      """"derivations": [""" +
      """{""" +
      """"owner": "main",""" +
      """"path": "44'/0'/0'",""" +
      """"chain_code": "d1bb833ecd3beed6ec5f6aa79d3a424d53f5b99147b21dbc00456b05bc978a71"""" +
      """},""" +
      """{""" +
      """"owner": "main",""" +
      """"path": "44'/0'",""" +
      """"pub_key": "04fb60043afe80ee1aeb0160e2aafc94690fb4427343e8d4bf410105b1121f7a44a311668fa80a7a341554a4ef5262bc6ebd8cc981b8b600dafd40f7682edb5b3b",""" +
      """"chain_code": "88c2281acd51737c912af74cc1d1a8ba564eb7925e0d58a5500b004ba76099cb"""" +
      """}""" +
      """]""" +
      """}"""
  private val MISSING_PATH_BODY =
    """{""" +
      """"account_index": 0,""" +
      """"derivations": [""" +
      """{""" +
      """"owner": "main",""" +
      """"pub_key": "0437bc83a377ea025e53eafcd18f299268d1cecae89b4f15401926a0f8b006c0f7ee1b995047b3e15959c5d10dd1563e22a2e6e4be9572aa7078e32f317677a901",""" +
      """"chain_code": "d1bb833ecd3beed6ec5f6aa79d3a424d53f5b99147b21dbc00456b05bc978a71"""" +
      """},""" +
      """{""" +
      """"owner": "main",""" +
      """"path": "44'/0'",""" +
      """"pub_key": "04fb60043afe80ee1aeb0160e2aafc94690fb4427343e8d4bf410105b1121f7a44a311668fa80a7a341554a4ef5262bc6ebd8cc981b8b600dafd40f7682edb5b3b",""" +
      """"chain_code": "88c2281acd51737c912af74cc1d1a8ba564eb7925e0d58a5500b004ba76099cb"""" +
      """}""" +
      """]""" +
      """}"""
  private val INVALID_JSON =
    """{""" +
      """"account_index": 0,""" +
      """"derivations": [""" +
      """{""" +
      """"owner": "main",""" +
      """"path": "44'/0'/0'",""" +
      """"pub_key": "0437bc83a377ea025e53eafcd18f299268d1cecae89b4f15401926a0f8b006c0f7ee1b995047b3e15959c5d10dd1563e22a2e6e4be9572aa7078e32f317677a901",""" +
      """"chain_code": "d1bb833ecd3beed6ec5f6aa79d3a424d53f5b99147b21dbc00456b05bc978a71"""" +
      """},""" +
      """{""" +
      """"owner": "main",""" +
      """"path": "44'/0'",""" +
      """"pub_key": "04fb60043afe80ee1aeb0160e2aafc94690fb4427343e8d4bf410105b1121f7a44a311668fa80a7a341554a4ef5262bc6ebd8cc981b8b600dafd40f7682edb5b3b",""" +
      """"chain_code": "88c2281acd51737c912af74cc1d1a8ba564eb7925e0d58a5500b004ba76099cb"""" +
      """}""" +
      """]"""
}
