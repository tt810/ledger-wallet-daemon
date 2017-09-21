package co.ledger.wallet.daemon.services

import java.util.concurrent.Executors

import co.ledger.wallet.daemon.DaemonConfiguration
import co.ledger.wallet.daemon.async.SerialExecutionContext
import co.ledger.wallet.daemon.database.DatabaseDao
import slick.jdbc.JdbcBackend.Database

import scala.concurrent.duration.Duration
import scala.concurrent.{Await, ExecutionContext}

object DatabaseService extends DaemonService {
  implicit val ec: ExecutionContext = new SerialExecutionContext()(ExecutionContext.fromExecutor(Executors.newSingleThreadExecutor()))
  lazy val dbDao: DatabaseDao = {
    val databaseDao = new DatabaseDao(Database.forConfig(DaemonConfiguration.dbProfileName))
    info("Start migration")
    Await.result(databaseDao.migrate(), Duration.Inf)
    info("Finish migration")
    databaseDao
  }
}
