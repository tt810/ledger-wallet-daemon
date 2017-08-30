package co.ledger.wallet.daemon.services

import java.sql.Timestamp
import java.util.Date
import javax.inject.Singleton

import co.ledger.wallet.daemon.Server
import co.ledger.wallet.daemon.Server.profile
import co.ledger.wallet.daemon.async.SerialExecutionContext

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class DatabaseService {
  implicit val ec: ExecutionContext = SerialExecutionContext.newInstance()
  import co.ledger.wallet.daemon.Server.profile.api._
  import co.ledger.wallet.daemon.database._

  def database: Future[profile.api.Database] = _conn

  private val _conn = migrate(Database.forConfig(Server.profileName)).recover {
    case all: Throwable =>
      all.printStackTrace()
      throw all
  }

  private def migrate(db: profile.api.Database) = {
    // Fetch database version
    db.run(databaseVersions.sortBy(_.version.desc).map(_.version).take(1).result) map { (result) =>
      result.head
    } recover {
      case all => -1
    } flatMap { (currentVersion) =>
      val maxVersion = Migrations.keys.toArray.sortWith(_ > _).head

      def migrate(version: Int): Future[Unit] = {
        if (version > maxVersion)
          Future.successful()
        else {
          val script = DBIO.seq(
            Migrations(version),
            databaseVersions += (version, new Timestamp(new Date().getTime))
          )
          db.run(script.transactionally) map { (_) =>
            migrate(version + 1)
          }
        }
      }
      migrate(currentVersion + 1)
    } map { (_) =>
      db
    }

  }

}
