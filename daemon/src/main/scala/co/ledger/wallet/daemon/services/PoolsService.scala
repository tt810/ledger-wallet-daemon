package co.ledger.wallet.daemon.services

import javax.inject.Singleton

import co.ledger.core.WalletPool
import co.ledger.wallet.daemon.database.User

import scala.concurrent.Future

@Singleton
class PoolsService {
  import PoolsService._
  def createPool(user: User, poolName: String, configuration: PoolConfiguration): Future[WalletPool] = ???
  def pools(user: User): Future[Array[WalletPool]] = ???
  def pool(user: User, poolName: String): Future[WalletPool] = ???
  def removePool(user: User, poolName: String): Future[Unit] = ???
}

object PoolsService {

  case class PoolConfiguration()

}