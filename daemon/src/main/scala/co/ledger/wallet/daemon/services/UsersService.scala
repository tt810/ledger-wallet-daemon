package co.ledger.wallet.daemon.services

import javax.inject.{Inject, Singleton}

import co.ledger.wallet.daemon.database.User

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

@Singleton
class UsersService extends DaemonService {
  private val dbDao = DatabaseService.dbDao

  def insertUser(publicKey: String, permissions: Long = 0): Future[Unit] = {
    info(s"Start to insert user with params: permissions=$permissions pubKey=$publicKey")
    dbDao.insertUser(User(publicKey, permissions)) map {_ =>
      ()
    }
  }
}
