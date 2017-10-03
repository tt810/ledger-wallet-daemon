package co.ledger.wallet.daemon.services

import javax.inject.{Inject, Singleton}

import co.ledger.wallet.daemon.database._
import co.ledger.wallet.daemon.models.{Bulk, Wallet, WalletsWithCount}

import scala.concurrent.Future


@Singleton
class WalletsService @Inject()(daemonCache: DaemonCache) extends DaemonService {

  def wallets(user: User, poolName: String, offset: Int, bulkSize: Int): Future[WalletsWithCount] = {
    info(LogMsgMaker.newInstance("Obtain wallets with params")
      .append("poolName", poolName)
      .append("offset", offset)
      .append("bulkSize", bulkSize)
      .append("userPubKey", user.pubKey)
      .toString())
    daemonCache.getWallets(Bulk(offset, bulkSize), poolName, user.pubKey)
  }

  def wallet(user: User, poolName: String, walletName: String): Future[Wallet] = {
    info(LogMsgMaker.newInstance("Obtain wallet with params")
      .append("walletName", walletName)
      .append("poolName", poolName)
      .append("userPubKey", user.pubKey)
      .toString())
    daemonCache.getWallet(walletName, poolName, user.pubKey)
  }

  def createWallet(user: User, poolName: String, walletName: String, currencyName: String): Future[Wallet] = {
    info(LogMsgMaker.newInstance("Create wallet with params")
      .append("walletName", walletName)
      .append("poolName", poolName)
      .append("extraParams", None)
      .append("userPubKey", user.pubKey)
      .toString())
    daemonCache.createWallet(walletName, currencyName, poolName, user)
  }

//  def removeWallet(user: User, poolName: String, walletName: String) = TODO once the method exists on the library
//    poolsService.pool(user, poolName) flatMap {(pool) =>
//      pool.
//
//    }

}
