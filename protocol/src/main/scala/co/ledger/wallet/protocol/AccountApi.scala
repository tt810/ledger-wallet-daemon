package co.ledger.wallet.protocol

import scala.concurrent.Future

case class AccountDescription(index: Int, synchronizing: Boolean)

trait AccountApi {
  def listAccounts(poolName: String, walletName: String): Future[AccountDescription]
  def createNextBip44AccountWithXpub(poolName: String, walletName: String, xpub: String): Future[Unit]
}
