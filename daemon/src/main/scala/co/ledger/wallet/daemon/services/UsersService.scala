package co.ledger.wallet.daemon.services

import javax.inject.{Inject, Singleton}

import co.ledger.wallet.daemon.database.User

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

@Singleton
class UsersService {
  private val dbDao = DatabaseService.dbDao

  def insertUser(publicKey: String, permissions: Long = 0): Future[Unit] =
    dbDao.insertUser(User(publicKey, permissions)) map {_ =>
      ()
    }
}
