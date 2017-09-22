package co.ledger.wallet.daemon.services

import javax.inject.{Inject, Singleton}

import scala.concurrent.Future
import co.ledger.wallet.daemon.database.{DefaultDaemonCache, User}
import co.ledger.wallet.daemon.models

import scala.concurrent.ExecutionContext.Implicits.global

@Singleton
class PoolsService @Inject()(daemonCache: DefaultDaemonCache) extends DaemonService {
  import PoolsService._

  def createPool(user: User, poolName: String, configuration: PoolConfiguration): Future[models.WalletPool] = {
    info(s"Start to create pool: poolName=$poolName configuration=$configuration userPubKey=${user.pubKey}")
    daemonCache.createPool(user.id.get, poolName, configuration.toString).flatMap(corePool => models.newInstance(corePool))
  }

  def pools(user: User): Future[Seq[models.WalletPool]] = {
    info(s"Obtain pools with params: userPubKey=${user.pubKey}")
    daemonCache.getPools(user.id.get).flatMap { pools =>
      Future.sequence(pools.map(corePool => models.newInstance(corePool)))
    }
  }

  def pool(user: User, poolName: String): Future[models.WalletPool] = {
    info(s"Obtain pool with params: poolName=$poolName userPubKey=${user.pubKey}")
    daemonCache.getPool(user.id.get, poolName).flatMap(models.newInstance(_))
  }

  def removePool(user: User, poolName: String): Future[Unit] = {
    info(s"Start to remove pool: poolName=$poolName userPubKey=${user.pubKey}")
    daemonCache.deletePool(user.id.get, poolName)
  }

}

object PoolsService {

  case class PoolConfiguration() {
    override def toString: String = ""
  }
}