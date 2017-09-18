package co.ledger.wallet.daemon.services

import java.util.concurrent.Executors
import javax.inject.Singleton

import co.ledger.wallet.daemon.database.DBMigrations._
import co.ledger.wallet.daemon.Server
import co.ledger.wallet.daemon.Server.profile
import co.ledger.wallet.daemon.async.SerialExecutionContext
import co.ledger.wallet.daemon.modules.{ConfigurationModule, DatabaseModule}

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class DatabaseService {
  implicit val ec: ExecutionContext = new SerialExecutionContext()(ExecutionContext.fromExecutor(Executors.newSingleThreadExecutor()))
  import co.ledger.wallet.daemon.Server.profile.api._
  import co.ledger.wallet.daemon.database._

  def database: Future[profile.api.Database] = _conn

  private val _conn = migrate(DatabaseModule.database(ConfigurationModule.configuration)).recover {
    case all: Throwable =>
      all.printStackTrace()
      throw all
  }

  private def migrate(db: profile.api.Database) = {
    // Fetch database version
    db.run(getLastMigrationVersion) recover {
      case _ => -1
    } flatMap { (currentVersion) =>
      val maxVersion = Migrations.keys.toArray.sortWith(_ > _).head

      def migrate(version: Int): Future[Unit] = {
        if (version > maxVersion)
          Future.successful()
        else {
          val migrationQ = DBIO.seq(Migrations(version),insertDatabaseVersion(version))
          db.run(migrationQ.transactionally) map { (_) =>
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
