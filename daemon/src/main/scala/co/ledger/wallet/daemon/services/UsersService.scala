package co.ledger.wallet.daemon.services

import javax.inject.{Inject, Singleton}

import co.ledger.wallet.daemon.database

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

@Singleton
class UsersService @Inject()(databaseService: DatabaseService) {
  import co.ledger.wallet.daemon.Server.profile.api._

  def insertUser(publicKey: String, permissions: Long = 0): Future[Unit] =
    databaseService.database flatMap { (db) =>
      db.run(database.insertUser(database.createUser(publicKey, permissions)).transactionally)
    } map {_ =>
      ()
    }

}
