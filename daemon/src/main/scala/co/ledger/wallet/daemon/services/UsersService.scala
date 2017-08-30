package co.ledger.wallet.daemon.services

import javax.inject.{Inject, Singleton}

import co.ledger.wallet.daemon.database._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

@Singleton
class UsersService @Inject()(databaseService: DatabaseService) {
  import co.ledger.wallet.daemon.Server.profile.api._

  def insertUser(publicKey: String): Future[Unit] = databaseService.database flatMap {(db) =>
    db.run(insertNewUserActions(User(None, publicKey)))
  } map {_ =>
    ()
  }

  private def insertNewUserActions(user: User) = (
    users.filter(_.pubKey === user.pubKey.bind).exists.result.flatMap {(exists) =>
      if (!exists) {
        users += user
      } else {
        DBIO.failed(new Exception(s"User ${user.pubKey} already exists"))
      }
    }
  ).transactionally
}
