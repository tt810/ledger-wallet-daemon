package co.ledger.wallet.daemon.services

import javax.inject.{Inject, Singleton}

import co.ledger.wallet.daemon.database.DaemonCache
import co.ledger.wallet.daemon.database.DefaultDaemonCache.User
import co.ledger.wallet.daemon.models.WalletPoolView
import co.ledger.wallet.daemon.schedulers.observers.SynchronizationResult

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class PoolsService @Inject()(daemonCache: DaemonCache) extends DaemonService {

  import PoolsService._

  def createPool(user: User, poolName: String, configuration: PoolConfiguration)
                (implicit ec: ExecutionContext): Future[WalletPoolView] = {
    info(LogMsgMaker.newInstance("Create wallet pool with params")
      .append("pool_name", poolName)
      .append("configuration", configuration)
      .append("user_pub_key", user.pubKey)
      .toString())
    daemonCache.createWalletPool(user, poolName, configuration.toString).flatMap(_.view)
  }

  def pools(user: User)(implicit ec: ExecutionContext): Future[Seq[WalletPoolView]] = {
    info(LogMsgMaker.newInstance("Obtain wallet pools with params")
      .append("user_pub_key", user.pubKey)
      .toString())
    daemonCache.getWalletPools(user.pubKey).flatMap { pools => Future.sequence(pools.map(_.view))}
  }

  def pool(user: User, poolName: String)(implicit ec: ExecutionContext): Future[Option[WalletPoolView]] = {
    info(LogMsgMaker.newInstance("Obtain wallet pool with params")
      .append("pool_name", poolName)
      .append("user_pub_key", user.pubKey)
      .toString())
    daemonCache.getWalletPool(user.pubKey, poolName).flatMap {
      case Some(pool) => pool.view.map(Option(_))
      case None => Future(None)
    }
  }

  def syncOperations()(implicit ec: ExecutionContext): Future[Seq[SynchronizationResult]] = {
    info(LogMsgMaker.newInstance("Synchronizing existing wallet pools").toString())
    daemonCache.syncOperations()
  }

  def removePool(user: User, poolName: String)(implicit ec: ExecutionContext): Future[Unit] = {
    info(LogMsgMaker.newInstance("Remove wallet pool with params")
      .append("pool_name", poolName)
      .append("user_pub_key", user.pubKey)
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