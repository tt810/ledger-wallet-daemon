package co.ledger.wallet.daemon.services

import javax.inject.{Inject, Singleton}

import co.ledger.core.{DynamicObject, Wallet}
import co.ledger.wallet.daemon.database.User
import co.ledger.core.implicits._

import scala.collection.JavaConverters._
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

@Singleton
class WalletsService @Inject()(poolsService: PoolsService) extends DaemonService {

  import WalletsService._

  def wallets(user: User, poolName: String, offset: Int, bulkSize: Int): Future[WalletBulk] = {
    info(s"Obtain wallets with params: poolName=$poolName offset=$offset bulkSize=$bulkSize userPubKey=${user.pubKey}")
    poolsService.pool(user, poolName) flatMap { (pool) =>
      pool.getWalletCount().flatMap { (count) =>
        pool.getWallets(offset, bulkSize) map { (wallets) =>
          val walletArr = wallets.asScala.toArray
          info(s"Wallets obtained: totalCount=$count size=${walletArr.size} walletNames=${walletArr.map(_.getName)}")
          WalletBulk(count, offset, bulkSize, walletArr)
        }
      }
    }
  }

  def wallet(user: User, poolName: String, walletName: String): Future[Wallet] = {
    info(s"Obtain wallet with params: poolName=$poolName walletName=$walletName userPubKey=${user.pubKey}")
    poolsService.pool(user, poolName).flatMap { pool =>
      pool.getWallet(walletName).map { wallet =>
        info(s"wallet obtained: wallet=$wallet")
        wallet
      }
    }
  }

  def createWallet(user: User, poolName: String, walletName: String, params: WalletCreationParameters): Future[Wallet] = {
    info(s"Start to create wallet: poolName=$poolName walletName=$walletName extraParams=$params userPubKey=${user.pubKey}")
    poolsService.pool(user, poolName) flatMap { (pool) =>
      pool.getCurrency(params.currency) flatMap { (currency) =>
        pool.createWallet(walletName, currency, params.configuration).map { wallet =>
          info(s"Finish creating wallet: $wallet")
          wallet
        }
      }
    }
  }

//  def removeWallet(user: User, poolName: String, walletName: String) = TODO once the method exists on the library
//    poolsService.pool(user, poolName) flatMap {(pool) =>
//      pool.
//
//    }

}

object WalletsService {

  case class WalletBulk(count: Int, offset: Int, bulkSize: Int, wallets: Array[Wallet])
  case class WalletCreationParameters(currency: String, configuration: DynamicObject)

}