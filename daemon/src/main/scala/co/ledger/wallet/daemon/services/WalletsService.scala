package co.ledger.wallet.daemon.services

import javax.inject.{Inject, Singleton}

import co.ledger.core.{DynamicObject, Wallet}
import co.ledger.wallet.daemon.database.User
import co.ledger.core.implicits._

import scala.collection.JavaConverters._
import scala.concurrent.Future

@Singleton
class WalletsService @Inject()(poolsService: PoolsService) {

  import WalletsService._

  def wallets(user: User, poolName: String, offset: Int, bulkSize: Int): Future[WalletBulk] =
    poolsService.pool(user, poolName) flatMap { (pool) =>
      pool.getWalletCount().flatMap { (count) =>
        pool.getWallets(offset, bulkSize) map { (wallets) =>
          WalletBulk(count, offset, bulkSize, wallets.asScala.toArray)
        }
      }
    }

  def wallet(user: User, poolName: String, walletName: String): Future[Wallet] =
    poolsService.pool(user, poolName).flatMap(_.getWallet(walletName))

  def createWallet(user: User, poolName: String, walletName: String, params: WalletCreationParameters): Future[Wallet] =
    poolsService.pool(user, poolName) flatMap { (pool) =>
      pool.getCurrency(params.currency) flatMap { (currency) =>
        pool.createWallet(walletName, currency, params.configuration)
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