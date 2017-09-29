package co.ledger.wallet.daemon.services

import javax.inject.{Inject, Singleton}

import scala.concurrent.{ExecutionContext, Future}
import co.ledger.wallet.daemon.database.{DefaultDaemonCache, User}
import co.ledger.wallet.daemon.models

@Singleton
class PoolsService @Inject()(daemonCache: DefaultDaemonCache) extends DaemonService {
  import PoolsService._

  def createPool(user: User, poolName: String, configuration: PoolConfiguration)(implicit ec: ExecutionContext): Future[models.WalletPool] = {
    info(LogMsgMaker.newInstance("Create wallet pool with params")
      .append("poolName", poolName)
      .append("configuration", configuration)
      .append("userPubKey", user.pubKey)
      .toString())
    daemonCache.createPool(user, poolName, configuration.toString).flatMap(corePool => models.newInstance(corePool))
  }

  def pools(user: User)(implicit ec: ExecutionContext): Future[Seq[models.WalletPool]] = {
    info(LogMsgMaker.newInstance("Obtain wallet pools with params")
      .append("userPubKey", user.pubKey)
      .toString())
    daemonCache.getPools(user.pubKey).flatMap { pools =>
      Future.sequence(pools.map(corePool => models.newInstance(corePool)))
    }
  }

  def pool(user: User, poolName: String)(implicit ec: ExecutionContext): Future[models.WalletPool] = {
    info(LogMsgMaker.newInstance("Obtain wallet pool with params")
      .append("poolName", poolName)
      .append("userPubKey", user.pubKey)
      .toString())
    daemonCache.getPool(user.pubKey, poolName).flatMap(models.newInstance(_))
  }

  def removePool(user: User, poolName: String)(implicit ec: ExecutionContext): Future[Unit] = {
    info(LogMsgMaker.newInstance("Remove wallet pool with params")
      .append("poolName", poolName)
      .append("userPubKey", user.pubKey)
      .toString())
    info(s"Start to remove pool: poolName=$poolName userPubKey=${user.pubKey}")
    daemonCache.deletePool(user, poolName)
  }

}

object PoolsService {

  case class PoolConfiguration() {
    override def toString: String = ""
  }
}