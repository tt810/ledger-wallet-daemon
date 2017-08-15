package co.ledger.wallet.protocol

import scala.concurrent.Future

case class WalletDescription(name: String, currency: String, accountNumber: Int, synchronizing: Boolean)

trait WalletApi {
  def listWallets(poolName: String):  Future[Either[RPCError, Array[WalletDescription]]]
  def createBitcoinLikeWallet(poolName: String, walletName: String, currency: String): Future[Either[RPCError, Unit]]
}
