package co.ledger.wallet.daemon.database

import co.ledger.wallet.daemon.models._

import scala.concurrent.Future

trait DaemonCache {

  // ************** account *************
  def getAccounts(pubKey: String, poolName: String, walletName: String): Future[Seq[AccountView]]

  def getAccount(accountIndex: Int, pubKey: String, poolName: String, walletName: String): Future[AccountView]

  def getNextAccountCreationInfo(pubKey: String, poolName: String, walletName: String, accountIndex: Option[Int]): Future[AccountDerivationView]

  def createAccount(accountDerivation: AccountDerivationView, user: UserDTO, poolName: String, walletName: String): Future[AccountView]

  // ************** currency ************
  def getCurrency(currencyName: String, poolName: String): Future[CurrencyView]

  def getCurrencies(poolName: String): Future[Seq[CurrencyView]]

  // ************** wallet *************
  def createWallet(walletName: String, currencyName: String, poolName: String, user: UserDTO): Future[WalletView]

  def getWallets(walletBulk: Bulk, poolName: String, pubKey: String): Future[WalletsViewWithCount]

  def getWallet(walletName: String, poolName: String, pubKey: String): Future[WalletView]

  // ************** wallet pool *************
  def createWalletPool(user: UserDTO, poolName: String, configuration: String): Future[WalletPoolView]

  def getWalletPool(pubKey: String, poolName: String): Future[WalletPoolView]

  def getWalletPools(pubKey: String): Future[Seq[WalletPoolView]]

  def deleteWalletPool(user: UserDTO, poolName: String): Future[Unit]

  //**************** user ***************
  def getUserDirectlyFromDB(pubKey: Array[Byte]): Future[Option[UserDTO]]

  def createUser(user: UserDTO): Future[Int]

}
