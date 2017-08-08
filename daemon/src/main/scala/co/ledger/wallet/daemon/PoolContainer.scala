package co.ledger.wallet.daemon

import java.nio.charset.Charset

import co.ledger.core._

import scala.concurrent.Future

class PoolContainer private (
                              val poolName: String,
                              val password: Option[String],
                              val configuration: DynamicObject,
                              val pathResolver: PathResolver,
                              val threadDispatcher: ThreadDispatcher,
                              val httpEngine: HttpClient,
                              val printer: LogPrinter,
                              val rng: RandomNumberGenerator,
                              val databaseBackend: DatabaseBackend,
                              val webSocketEngine: WebSocketClient,
                              val pool: WalletPool
                            ) {

}

object PoolContainer {

  def open(poolName: String, password: Option[String], databaseBackendName: Option[String],
           dbConnectionString: Option[String], configuration: Option[String]): Future[PoolContainer] = {
      val conf = DynamicObject.load(configuration.getOrElse("{}").getBytes)

      ???
  }

}