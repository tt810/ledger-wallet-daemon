package co.ledger.wallet.daemon.services

import javax.inject.{Inject, Singleton}

import co.ledger.core.{Account, BitcoinLikeNextAccountInfo}
import co.ledger.wallet.daemon.database.{DaemonCache, User}
import co.ledger.wallet.daemon.models
import co.ledger.wallet.daemon.models.AccountDerivation

import scala.concurrent.Future

@Singleton
class AccountsService @Inject()(defaultDaemonCache: DaemonCache) extends DaemonService {

  import AccountsService._

  def accounts(user: User, poolName: String, walletName: String): Future[Seq[models.Account]] = {
    info(LogMsgMaker.newInstance("Obtain accounts with params")
      .append("pool_name", poolName)
      .append("wallet_name", walletName)
      .append("user_pub_key", user.pubKey)
      .toString())
    defaultDaemonCache.getAccounts(user.pubKey, poolName, walletName)
  }

  def account(accountIndex: Int, user: User, poolName: String, walletName: String): Future[models.Account] = {
    info(LogMsgMaker.newInstance("Obtain account with params")
      .append("account_index", accountIndex)
      .append("pool_name", poolName)
      .append("wallet_name", walletName)
      .append("user_pub_key", user.pubKey)
      .toString())
    defaultDaemonCache.getAccount(accountIndex, user.pubKey, poolName, walletName)
  }

  def nextAccountCreationInfo(user: User, poolName: String, walletName: String, accountIndex: Option[Int]): Future[AccountDerivation] = {
    info(LogMsgMaker.newInstance("Obtain next available account creation information")
      .append("account_index", accountIndex)
      .append("pool_name", poolName)
      .append("wallet_name", walletName)
      .append("user_pub_key", user.pubKey)
      .toString())
    defaultDaemonCache.getNextAccountCreationInfo(user.pubKey, poolName, walletName, accountIndex)
  }

  def createAccount(accountCreationBody: AccountDerivation, user: User, poolName: String, walletName: String): Future[models.Account] = {
    info(LogMsgMaker.newInstance("Create account with params")
      .append("account_derivations", accountCreationBody)
      .append("pool_name", poolName)
      .append("wallet_name", walletName)
      .append("user_pub_key", user.pubKey)
      .toString())
    defaultDaemonCache.createAccount(accountCreationBody, user, poolName, walletName)
  }

//  def createBitcoinAccount(user: User, poolName: String, walletName: String, params: BitcoinAccountCreationParameters) = {
//    info(s"Start to create Bitcoin account: poolName=$poolName walletName=$walletName extraParams=$params userPubKey=${user.pubKey}")
//    walletsService.wallet(user, poolName, walletName) flatMap { (wallet) =>
//      null
//    }
//  }

  def getNextAccountInfo(user: User, poolName: String, walletName: String): Future[NextAccountInformation] = ???
//  {
//    info(s"Obtain next account information: poolName=$poolName walletName=$walletName userPubKey=${user.pubKey}")
//    walletsService.wallet(user, poolName, walletName).flatMap { (wallet) =>
//      wallet.getWalletType match {
//        case WalletType.BITCOIN => getBitcoinLikeNextAccountInfo(wallet)
//        case WalletType.ETHEREUM => ???
//        case WalletType.RIPPLE => ???
//        case WalletType.MONERO => ???
//      }
//    }
//  }
//  private def getBitcoinLikeNextAccountInfo(wallet: Wallet) =
//    wallet.asBitcoinLikeWallet().getNextAccountInfo().map(new BLNextAccountInformation(_))


//  def removeAccount(user: User, poolName: String, walletName: String, accountIndex: Int) = TODO implement once exists on the lib
//    walletsService.wallet(user, poolName, walletName) flatMap {(wallet) =>
//      wallet.
//    }

}

object AccountsService {
  case class AccountBulk(count: Int, offset: Int, bulkSize: Int, accounts: Array[Account])
  case class BitcoinAccountCreationParameters()
  trait NextAccountInformation {
    def index: Int
  }
  case class BLNextAccountInformation(override val index: Int, xpubPath: String, accountNodePath: String, parentNodePath: String) extends NextAccountInformation {
    def this(info: BitcoinLikeNextAccountInfo) {
      this(info.getIndex, info.getXpubPath, info.getAccountNodePath, info.getParentNodePath)
    }
  }
}