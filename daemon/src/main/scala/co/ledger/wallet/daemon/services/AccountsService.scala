package co.ledger.wallet.daemon.services

import javax.inject.{Inject, Singleton}

import co.ledger.core.{Account, BitcoinLikeNextAccountInfo, Wallet, WalletType}
import co.ledger.wallet.daemon.database.User
import co.ledger.core.implicits._

import scala.collection.JavaConverters._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

@Singleton
class AccountsService @Inject()(walletsService: WalletsService) extends DaemonService {

  import AccountsService._

  def accounts(user: User, poolName: String, walletName: String, offset: Int, bulkSize: Int) = {
    info(s"Obtain accounts with params: poolName=$poolName walletName=$walletName offset=$offset bulkSize=$bulkSize userPubKey=${user.pubKey}")
    walletsService.wallet(user, poolName, walletName) flatMap { (wallet) =>
      wallet.getAccountCount() flatMap { (count) =>
        wallet.getAccounts(offset, bulkSize) map { (accounts) =>
          AccountBulk(count, offset, bulkSize, accounts.asScala.toArray)
        }
      }
    }
  }

  def account(user: User, poolName: String, walletName: String, accountIndex: Int) = {
    info(s"Obtain account with params: poolName=$poolName walletName=$walletName accountIndex=$accountIndex userPubKey=${user.pubKey}")
    walletsService.wallet(user, poolName, walletName).flatMap(_.getAccount(accountIndex))
  }

  def createBitcoinAccount(user: User, poolName: String, walletName: String, params: BitcoinAccountCreationParameters) = {
    info(s"Start to create Bitcoin account: poolName=$poolName walletName=$walletName extraParams=$params userPubKey=${user.pubKey}")
    walletsService.wallet(user, poolName, walletName) flatMap { (wallet) =>
      null
    }
  }

  def getNextAccountInfo(user: User, poolName: String, walletName: String): Future[NextAccountInformation] = {
    info(s"Obtain next account information: poolName=$poolName walletName=$walletName userPubKey=${user.pubKey}")
    walletsService.wallet(user, poolName, walletName).flatMap { (wallet) =>
      wallet.getWalletType match {
        case WalletType.BITCOIN => getBitcoinLikeNextAccountInfo(wallet)
        case WalletType.ETHEREUM => ???
        case WalletType.RIPPLE => ???
        case WalletType.MONERO => ???
      }
    }
  }
  private def getBitcoinLikeNextAccountInfo(wallet: Wallet) =
    wallet.asBitcoinLikeWallet().getNextAccountInfo().map(new BLNextAccountInformation(_))


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