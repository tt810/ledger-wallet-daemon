package co.ledger.wallet.daemon.services

import javax.inject.{Inject, Singleton}

import scala.concurrent.Future
import co.ledger.wallet.daemon.database.{DaemonCache, User}
import co.ledger.wallet.daemon.models.WalletPool

@Singleton
class PoolsService @Inject()(daemonCache: DaemonCache) extends DaemonService {
  import PoolsService._

  def createPool(user: User, poolName: String, configuration: PoolConfiguration): Future[WalletPool] = {
    info(LogMsgMaker.newInstance("Create wallet pool with params")
      .append("poolName", poolName)
      .append("configuration", configuration)
      .append("userPubKey", user.pubKey)
      .toString())
    daemonCache.createWalletPool(user, poolName, configuration.toString)
  }

  def pools(user: User): Future[Seq[WalletPool]] = {
    info(LogMsgMaker.newInstance("Obtain wallet pools with params")
      .append("userPubKey", user.pubKey)
      .toString())
    daemonCache.getWalletPools(user.pubKey)
  }

  def pool(user: User, poolName: String): Future[WalletPool] = {
    info(LogMsgMaker.newInstance("Obtain wallet pool with params")
      .append("poolName", poolName)
      .append("userPubKey", user.pubKey)
      .toString())
    daemonCache.getWalletPool(user.pubKey, poolName)
  }

  def removePool(user: User, poolName: String): Future[Unit] = {
    info(LogMsgMaker.newInstance("Remove wallet pool with params")
      .append("poolName", poolName)
      .append("userPubKey", user.pubKey)
      .toString())
    info(s"Start to remove pool: poolName=$poolName userPubKey=${user.pubKey}")
    daemonCache.deleteWalletPool(user, poolName)
  }

}

object PoolsService {

  case class PoolConfiguration() {
    override def toString: String = ""
  }
}