package co.ledger.wallet.daemon.api

import co.ledger.wallet.daemon.utils.APIFeatureTest
import com.twitter.finagle.http.{Response, Status}

/**
  * Test cases for [[co.ledger.wallet.daemon.controllers.TransactionsController]].
  *
  * User: Ting Tu
  * Date: 25-04-2018
  * Time: 11:26
  *
  */
class TransactionsApiTest extends APIFeatureTest {

  test("TransactionsApi#Create transaction") {
    val poolName = "transactionsAPI1"
    createPool(poolName)
    assertWalletCreation(poolName, "wallet", "bitcoin", Status.Ok)
    assertCreateAccount(ACCOUNT_BODY, poolName, "wallet", Status.Ok)
    assertSyncPool(Status.Ok)
    assertCreateTransaction(TX_BODY_WITH_EXCLUDE_UTXO,poolName,"wallet",0,Status.Ok)
    assertCreateTransaction(TX_BODY,poolName,"wallet",0,Status.Ok)
    assertCreateTransaction(INVALID_FEE_LEVEL_BODY,poolName,"wallet",0,Status.BadRequest)
  }

  private def assertCreateTransaction(tx: String, poolName: String, walletName: String, accountIndex: Int, expected: Status): Response = {
    server.httpPost(
      s"/pools/$poolName/wallets/$walletName/accounts/$accountIndex/transactions",
      tx,
      headers = defaultHeaders,
      andExpect = expected
    )
  }

  private val INVALID_FEE_LEVEL_BODY =
    """{""" +
      """"recipient": "36v1GRar68bBEyvGxi9RQvdP6Rgvdwn2C2",""" +
      """"fee_level": "OTHER",""" +
      """"amount": 10000""" +
      """}"""

  private val TX_BODY =
    """{""" +
      """"recipient": "36v1GRar68bBEyvGxi9RQvdP6Rgvdwn2C2",""" +
      """"fee_amount": 397000,""" +
      """"fee_level": "FAST",""" +
      """"amount": 10000""" +
      """}"""

  private val TX_BODY_WITH_EXCLUDE_UTXO =
    """{""" +
    """"recipient": "36v1GRar68bBEyvGxi9RQvdP6Rgvdwn2C2",""" +
    """"fee_level": "NORMAL",""" +
    """"amount": 2000,""" +
    """"exclude_utxos":{"6f6025991691d26f9bb329568d452b728978fa34b418375c31845b3698b797d7": 0 }""" +
    """}"""
  private val ACCOUNT_BODY =
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

}
