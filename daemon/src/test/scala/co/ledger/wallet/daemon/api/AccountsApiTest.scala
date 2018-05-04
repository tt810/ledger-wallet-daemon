package co.ledger.wallet.daemon.api

import java.util.UUID

import co.ledger.wallet.daemon.models.{AccountDerivationView, AccountView}
import co.ledger.wallet.daemon.services.OperationQueryParams
import co.ledger.wallet.daemon.utils.APIFeatureTest
import com.twitter.finagle.http.{Response, Status}

class AccountsApiTest extends APIFeatureTest {

  test("AccountsApi#Get empty accounts") {
    createPool("account_pool")
    assertWalletCreation("account_pool", "account_wallet", "bitcoin", Status.Ok)
    val result = assertGetAccounts(None, "account_pool", "account_wallet", Status.Ok)
    val expectedResponse = List[AccountView]()
    assert(expectedResponse === parse[Seq[AccountView]](result))
    deletePool("account_pool")
  }

  test("AccountsApi#Get account with index from empty wallet") {
    createPool("account_pool")
    assertWalletCreation("account_pool", "individual_account_wallet", "bitcoin", Status.Ok)
    assertGetAccounts(Option(1), "account_pool", "individual_account_wallet", Status.NotFound)
    deletePool("account_pool")
  }

  test("AccountsApi#Get accounts same as get individual account") {
    createPool("list_pool")
    assertWalletCreation("list_pool", "account_wallet", "bitcoin", Status.Ok)
    val expectedAccount = parse[AccountView](assertCreateAccount(CORRECT_BODY, "list_pool", "account_wallet", Status.Ok))
    val actualAccount = parse[AccountView](assertGetAccounts(Option(0), "list_pool", "account_wallet", Status.Ok))
    assert(expectedAccount === actualAccount)
    val actualAccountList = parse[Seq[AccountView]](assertGetAccounts(None, "list_pool", "account_wallet", Status.Ok))
    assert(List(actualAccount) === actualAccountList)
    deletePool("list_pool")
  }

  test("AccountsApi#Get fresh addresses from account") {
    createPool("fresh_addresses_pool")
    assertWalletCreation("fresh_addresses_pool", "account_wallet", "bitcoin", Status.Ok)
    assertCreateAccount(CORRECT_BODY, "fresh_addresses_pool", "account_wallet", Status.Ok)
    val addresses = parse[Seq[String]](assertGetFreshAddresses("fresh_addresses_pool", "account_wallet", index = 0, Status.Ok))
    assert(!addresses.isEmpty)
  }

  test("AccountsApi#Get account(s) from non exist pool return bad request") {
    assertCreateAccount(CORRECT_BODY, "not_exist_pool", "account_wallet", Status.BadRequest)
    assertGetAccounts(None, "not_exist_pool", "account_wallet", Status.BadRequest)
    assertGetAccounts(Option(0), "not_exist_pool", "account_wallet", Status.BadRequest)
  }

  test("AccountsApi#Get account(s) from non exist wallet return bad request") {
    createPool("exist_pool")
    assertCreateAccount(CORRECT_BODY, "exist_pool", "not_exist_wallet", Status.BadRequest)
    assertGetAccounts(None, "exist_pool", "not_exist_wallet", Status.BadRequest)
    assertGetAccounts(Option(0), "exist_pool", "not_exist_wallet", Status.BadRequest)
    deletePool("exist_pool")
  }

  test("AccountsApi#Get next account creation info with index return Ok") {
    createPool("info_pool")
    assertWalletCreation("info_pool", "account_wallet", "bitcoin", Status.Ok)
    val actualResult = parse[AccountDerivationView](assertGetAccountCreationInfo("info_pool", "account_wallet", Option(0), Status.Ok))
    assert(0 === actualResult.accountIndex)
    deletePool("info_pool")
  }

  test("AccountsApi#Get next account creation info without index return Ok") {
    createPool("info_pool")
    assertWalletCreation("info_pool", "account_wallet", "bitcoin", Status.Ok)
    assertGetAccountCreationInfo("info_pool", "account_wallet", None, Status.Ok)
    deletePool("info_pool")
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

  test("AccountsApi#Create account on btc testnet") {
    createPool("test_pool")
    assertWalletCreation("test_pool", "accounts_wallet", "bitcoin_testnet", Status.Ok)
    assertCreateAccount(CORRECT_BODY, "test_pool", "accounts_wallet", Status.Ok)
    deletePool("test_pool")
  }

  test("AccountsApi#Create account with request body as invalid json") {
    createPool("account_pool")
    assertWalletCreation("account_pool", "accounts_wallet", "bitcoin", Status.Ok)
    assertCreateAccount(INVALID_JSON, "account_pool", "accounts_wallet", Status.BadRequest)
    deletePool("account_pool")
  }

  test("AccountsApi#Get account operations") {

    def getUUID(field: String, content: Map[String, Any]): Option[UUID] = {
      val idStr = content.get(field).asInstanceOf[Option[String]]
      idStr.map(UUID.fromString(_))
    }

    createPool("op_pool")
    assertWalletCreation("op_pool", "op_wallet", "bitcoin", Status.Ok)
    assertCreateAccount(CORRECT_BODY, "op_pool", "op_wallet", Status.Ok)
    assertSyncPool(Status.Ok)
    assertGetAccountOp("op_pool", "op_wallet", 0, "noexistop", 0, Status.NotFound)
    assertGetAccountOp("op_pool", "op_wallet", 0, "bcbe563a94e70a6fe0a190d6578f5440615eb64bbd6c49b2a6b77eb9ba01963a", 0, Status.Ok)

    val firstBtch = parse[Map[String, Any]](assertGetAccountOps("op_pool", "op_wallet", 0, OperationQueryParams(None, None, 2, 0), Status.Ok))

    val secondBtch = parse[Map[String, Any]](assertGetAccountOps("op_pool", "op_wallet", 0, OperationQueryParams(None, getUUID("next", firstBtch), 10, 0), Status.Ok))

    val previousOf2ndBtch = parse[Map[String, Any]](assertGetAccountOps("op_pool", "op_wallet", 0, OperationQueryParams(getUUID("previous", secondBtch), None, 10, 0), Status.Ok))
    assert(firstBtch.get("next") === previousOf2ndBtch.get("next"))
    assert(firstBtch.get("previous") === previousOf2ndBtch.get("previous"))

    val thirdBtch = parse[Map[String, Any]](assertGetAccountOps("op_pool", "op_wallet", 0, OperationQueryParams(None, getUUID("next", secondBtch), 5, 0), Status.Ok))

    val fourthBtch = parse[Map[String, Any]](assertGetAccountOps("op_pool", "op_wallet", 0, OperationQueryParams(None, getUUID("next", thirdBtch), 10, 0), Status.Ok))

    val previousOf4thBtch = parse[Map[String, Any]](assertGetAccountOps("op_pool", "op_wallet", 0, OperationQueryParams(getUUID("previous", fourthBtch), None, 10, 1), Status.Ok))
    assert(thirdBtch.get("next") === previousOf4thBtch.get("next"))
    assert(thirdBtch.get("previous") === previousOf4thBtch.get("previous"))
    deletePool("op_pool")
  }

  test("AccountsApi#Pool not exist") {
    createPool("op_pool_mal")
    assertWalletCreation("op_pool_mal", "op_wallet", "bitcoin", Status.Ok)
    assertCreateAccount(CORRECT_BODY, "op_pool_mal", "op_wallet", Status.Ok)

    assertGetAccountOps("op_pool_non_exist", "op_wallet", 0, OperationQueryParams(None, None, 2, 0), Status.BadRequest)
    assertGetAccountOps("op_pool_mal", "op_wallet", 0, OperationQueryParams(None, Option(UUID.randomUUID), 2, 0), Status.BadRequest)
    assertGetAccountOps("op_pool_mal", "op_wallet", 0, OperationQueryParams(Option(UUID.randomUUID), None, 2, 0), Status.BadRequest)
    deletePool("op_pool_mal")
  }

  private def assertGetAccountOp(poolName: String, walletName: String, accountIndex: Int, uid: String, fullOp: Int, expected: Status): Response = {
    val sb = new StringBuilder(s"/pools/$poolName/wallets/$walletName/accounts/$accountIndex/operations/$uid?full_op=$fullOp")
    server.httpGet(sb.toString(), headers = defaultHeaders, andExpect = expected)
  }

  private def assertGetAccountOps(poolName: String, walletName: String, accountIndex: Int, params: OperationQueryParams, expected: Status): Response = {
    val sb = new StringBuilder(s"/pools/$poolName/wallets/$walletName/accounts/$accountIndex/operations?")
    params.previous match {
      case None =>
      case Some(p) => sb.append("previous=" + p.toString + "&")
    }
    params.next match {
      case None =>
      case Some(n) => sb.append("next=" + n.toString + "&")
    }
    sb.append(s"batch=${params.batch}&full_op=${params.fullOp}")
    val previous = if (params.previous.isEmpty) null else params.previous.get
    val next = if (params.next.isEmpty) null else params.next.get
    server.httpGet(sb.toString(), headers = defaultHeaders, andExpect = expected)
  }

  private def assertGetAccounts(index: Option[Int], poolName: String, walletName: String, expected: Status): Response = {
    index match {
      case None => server.httpGet(s"/pools/$poolName/wallets/$walletName/accounts", headers = defaultHeaders, andExpect = expected)
      case Some(i) => server.httpGet(s"/pools/$poolName/wallets/$walletName/accounts/$i", headers = defaultHeaders, andExpect = expected)
    }
  }

  private def assertGetAccountCreationInfo(poolName: String, walletName: String, index: Option[Int], expected: Status): Response = {
    index match {
      case None => server.httpGet(s"/pools/$poolName/wallets/$walletName/accounts/next", headers = defaultHeaders, andExpect = expected)
      case Some(i) => server.httpGet(s"/pools/$poolName/wallets/$walletName/accounts/next?account_index=$i", headers = defaultHeaders, andExpect = expected)
    }

  }

  private def assertGetFreshAddresses(poolName: String, walletName: String, index: Int, expected: Status): Response = {
    server.httpGet(s"/pools/$poolName/wallets/$walletName/accounts/$index/addresses/fresh", headers = defaultHeaders, andExpect = expected)
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
