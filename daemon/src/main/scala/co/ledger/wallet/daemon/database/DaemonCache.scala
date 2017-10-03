package co.ledger.wallet.daemon.database

import co.ledger.wallet.daemon.models._

import scala.concurrent.Future

trait DaemonCache {

  // ************** account *************
  def getAccounts(pubKey: String, poolName: String, walletName: String): Future[Seq[Account]]

  def getAccount(accountIndex: Int, pubKey: String, poolName: String, walletName: String): Future[Account]

  def getNextAccountCreationInfo(pubKey: String, poolName: String, walletName: String, accountIndex: Option[Int]): Future[AccountDerivation]

  def createAccount(accountDerivation: AccountDerivation, user: User, poolName: String, walletName: String): Future[Account]

  // ************** currency ************
  def getCurrency(currencyName: String, poolName: String): Future[Currency]

  def getCurrencies(poolName: String): Future[Seq[Currency]]

  // ************** wallet *************
  def createWallet(walletName: String, currencyName: String, poolName: String, user: User): Future[Wallet]

  def getWallets(walletBulk: Bulk, poolName: String, pubKey: String): Future[WalletsWithCount]

  def getWallet(walletName: String, poolName: String, pubKey: String): Future[Wallet]

  // ************** wallet pool *************
  def createWalletPool(user: User, poolName: String, configuration: String): Future[WalletPool]

  def getWalletPool(pubKey: String, poolName: String): Future[WalletPool]

  def getWalletPools(pubKey: String): Future[Seq[WalletPool]]

  def deleteWalletPool(user: User, poolName: String): Future[Unit]

  //**************** user ***************
  def getUserDirectlyFromDB(pubKey: Array[Byte]): Future[Option[User]]

  def createUser(user: User): Future[Int]

}
