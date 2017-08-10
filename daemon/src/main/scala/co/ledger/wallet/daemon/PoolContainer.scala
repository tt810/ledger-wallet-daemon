package co.ledger.wallet.daemon

import java.nio.charset.Charset

import co.ledger.core._
import co.ledger.wallet.daemon.exceptions.LedgerCoreException
import co.ledger.wallet.daemon.libledger_core.async.ScalaThreadDispatcher
import co.ledger.wallet.daemon.libledger_core.crypto.SecureRandomRNG
import co.ledger.wallet.daemon.libledger_core.debug.NoOpLogPrinter
import co.ledger.wallet.daemon.libledger_core.filesystem.ScalaPathResolver
import co.ledger.wallet.daemon.libledger_core.net.ScalaHttpClient

import scala.concurrent.{Future, Promise}

class PoolContainer private (val pool: WalletPool) {

}

object PoolContainer {

  def open(poolName: String, password: Option[String], databaseBackendName: Option[String],
           dbConnectionString: Option[String], configuration: Option[String]): Future[PoolContainer] = {
      val conf = DynamicObject.load(configuration.getOrElse("{}").getBytes)
      val promise = Promise[PoolContainer]()
      val dispatcher = new ScalaThreadDispatcher(LedgerWalletDaemon.manager.ec)
      val backend = databaseBackendName.getOrElse("sqlite") match {
        case "sqlite" => DatabaseBackend.getSqlite3Backend
        case others => throw new Exception(s"Unknown database named '$databaseBackendName'")
      }
      val builder = WalletPoolBuilder
        .createInstance()
        .setName(poolName)
        .setConfiguration(conf)
        .setHttpClient(new ScalaHttpClient)
        .setWebsocketClient(null)
        .setLogPrinter(new NoOpLogPrinter(dispatcher.getMainExecutionContext))
        .setPathResolver(new ScalaPathResolver(poolName))
        .setRandomNumberGenerator(new SecureRandomRNG)
        .setThreadDispatcher(dispatcher)
        .setDatabaseBackend(backend)
      password.foreach(builder.setPassword)
      builder.build(new WalletPoolCallback {
        override def onCallback(result: WalletPool, error: Error): Unit = {
          if (result != null) {
            promise.success(new PoolContainer(result))
          } else {
            promise.failure(LedgerCoreException(error))
          }
        }
      })
      promise.future
  }

}