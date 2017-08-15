package co.ledger.wallet.daemon.api

import co.ledger.core
import co.ledger.core.{I32Callback, Wallet}
import co.ledger.wallet.daemon.LedgerWalletDaemon
import co.ledger.wallet.daemon.exceptions.LedgerCoreException
import co.ledger.wallet.protocol
import co.ledger.wallet.protocol.{WalletApi, WalletDescription}

import scala.concurrent.{Future, Promise}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.Success
import co.ledger.core.implicits._
import scala.collection.JavaConverters._

class WalletApiImpl extends WalletApi {
  override def listWallets(poolName: String): Future[Either[protocol.RPCError, Array[WalletDescription]]] = {
    LedgerWalletDaemon.manager.getPool(poolName) flatMap {(pool) =>
      pool.pool.getWalletCount().flatMap {(count) =>
        pool.pool.getWallets(0, count)
      }
    } map {(wallets) =>
      wallets.asScala.toArray map {(wallet) =>
        WalletDescription(wallet.getName, wallet.getCurrency.getName, 0, false)
      }
    } recover {
      case all: Throwable =>
        all.printStackTrace()
        throw all
    } transform protocolize
  }

  override def createBitcoinLikeWallet(poolName: String, walletName: String, currency: String): Future[Either[protocol.RPCError, Unit]] = ???
}
