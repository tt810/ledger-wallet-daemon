package co.ledger.wallet.daemon.services

import javax.inject.{Inject, Singleton}

import co.ledger.core.{DynamicObject, Wallet => CoreWallet}
import co.ledger.wallet.daemon.database.{DefaultDaemonCache, User}
import co.ledger.core.implicits._

import scala.collection.JavaConverters._
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

@Singleton
class WalletsService @Inject()(daemonCache: DefaultDaemonCache) extends DaemonService {

  import WalletsService._

  def wallets(user: User, poolName: String, offset: Int, bulkSize: Int): Future[WalletBulk] = {
    info(s"Obtain wallets with params: poolName=$poolName offset=$offset bulkSize=$bulkSize userPubKey=${user.pubKey}")
    daemonCache.getPool(user.id.get, poolName).flatMap { corePool =>
      corePool.getWalletCount().flatMap { (count) =>
        corePool.getWallets(offset, bulkSize) map { (wallets) =>
          val walletArr = wallets.asScala.toArray
          info(s"Wallets obtained: totalCount=$count size=${walletArr.size} walletNames=${walletArr.map(_.getName)}")
          WalletBulk(count, offset, bulkSize, walletArr)
        }
      }
    }
  }

  def wallet(user: User, poolName: String, walletName: String): Future[CoreWallet] = {
    info(s"Obtain wallet with params: poolName=$poolName walletName=$walletName userPubKey=${user.pubKey}")
    daemonCache.getPool(user.id.get, poolName).flatMap {corePool =>
      corePool.getWallet(walletName).map { wallet =>
        info(s"wallet obtained: wallet=$wallet")
        wallet
      }
    }
  }

  def createWallet(user: User, poolName: String, walletName: String, params: WalletCreationParameters): Future[CoreWallet] = {
    info(s"Start to create wallet: poolName=$poolName walletName=$walletName extraParams=$params userPubKey=${user.pubKey}")
    daemonCache.getPool(user.id.get, poolName).flatMap {corePool =>
      corePool.getCurrency(params.currency) flatMap { (currency) =>
        corePool.createWallet(walletName, currency, params.configuration).map { wallet =>
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

  case class WalletBulk(count: Int, offset: Int, bulkSize: Int, wallets: Array[CoreWallet])
  case class WalletCreationParameters(currency: String, configuration: DynamicObject)

}