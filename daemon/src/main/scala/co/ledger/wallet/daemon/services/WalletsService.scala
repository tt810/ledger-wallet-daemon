package co.ledger.wallet.daemon.services

import javax.inject.{Inject, Singleton}

import co.ledger.wallet.daemon.database._
import co.ledger.wallet.daemon.models.{PackedOperationsView, WalletView, WalletsViewWithCount}

import scala.concurrent.Future


@Singleton
class WalletsService @Inject()(daemonCache: DaemonCache) extends DaemonService {

  def wallets(user: UserDto, poolName: String, offset: Int, bulkSize: Int): Future[WalletsViewWithCount] = {
    info(LogMsgMaker.newInstance("Obtain wallets with params")
      .append("pool_name", poolName)
      .append("offset", offset)
      .append("bulk_size", bulkSize)
      .append("user_pub_key", user.pubKey)
      .toString())
    daemonCache.getWallets(Bulk(offset, bulkSize), poolName, user.pubKey)
  }

  def wallet(user: UserDto, poolName: String, walletName: String): Future[WalletView] = {
    info(LogMsgMaker.newInstance("Obtain wallet with params")
      .append("wallet_name", walletName)
      .append("pool_name", poolName)
      .append("user_pub_key", user.pubKey)
      .toString())
    daemonCache.getWallet(walletName, poolName, user.pubKey)
  }

  def createWallet(user: UserDto, poolName: String, walletName: String, currencyName: String): Future[WalletView] = {
    info(LogMsgMaker.newInstance("Create wallet with params")
      .append("wallet_name", walletName)
      .append("pool_name", poolName)
      .append("extra_params", None)
      .append("user_pub_key", user.pubKey)
      .toString())
    daemonCache.createWallet(walletName, currencyName, poolName, user)
  }

}
