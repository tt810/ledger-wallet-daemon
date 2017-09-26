package co.ledger.wallet.daemon.services

import javax.inject.{Inject, Singleton}

import co.ledger.core.{Wallet => CoreWallet}
import co.ledger.wallet.daemon.database.{Bulk, DefaultDaemonCache, User, WalletsWithCount}

import scala.concurrent.Future


@Singleton
class WalletsService @Inject()(daemonCache: DefaultDaemonCache) extends DaemonService {

  def wallets(user: User, poolName: String, offset: Int, bulkSize: Int): Future[WalletsWithCount] = {
    info(s"Obtain wallets with params: poolName=$poolName offset=$offset bulkSize=$bulkSize userPubKey=${user.pubKey}")
    daemonCache.getWallets(Bulk(offset, bulkSize), poolName, user.pubKey)
  }

  def wallet(user: User, poolName: String, walletName: String): Future[CoreWallet] = {
    info(s"Obtain wallet with params: poolName=$poolName walletName=$walletName userPubKey=${user.pubKey}")
    daemonCache.getWallet(walletName, poolName, user.pubKey)
  }

  def createWallet(user: User, poolName: String, walletName: String, currencyName: String): Future[CoreWallet] = {
    info(s"Start to create wallet: poolName=$poolName walletName=$walletName extraParams=none userPubKey=${user.pubKey}")
    daemonCache.createWallet(walletName, currencyName, poolName, user)
  }

//  def removeWallet(user: User, poolName: String, walletName: String) = TODO once the method exists on the library
//    poolsService.pool(user, poolName) flatMap {(pool) =>
//      pool.
//
//    }

}
