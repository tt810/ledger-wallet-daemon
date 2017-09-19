package co.ledger.wallet.daemon.services

import java.util.concurrent.Executors

import co.ledger.wallet.daemon.DaemonConfiguration
import co.ledger.wallet.daemon.async.SerialExecutionContext
import co.ledger.wallet.daemon.database.DatabaseDao
import slick.jdbc.JdbcBackend.Database

import scala.concurrent.ExecutionContext

object DatabaseService {
  implicit val ec: ExecutionContext = new SerialExecutionContext()(ExecutionContext.fromExecutor(Executors.newSingleThreadExecutor()))
  lazy val dbDao: DatabaseDao = {
    val databaseDao = new DatabaseDao(Database.forConfig(DaemonConfiguration.dbProfileName))
    println("XXXXXXXXXXXXXXXXX start migration XXXXXXXXXXXXXX") // TODO: add logging
    databaseDao.migrate.recover {
      case all: Throwable =>
        all.printStackTrace()
        throw all
    }
    println("XXXXXXXXXXXXXXXXX finish migration XXXXXXXXXXXXXX")
    databaseDao
  }
}
