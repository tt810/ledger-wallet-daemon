package co.ledger.wallet.daemon.clients

import co.ledger.wallet.daemon.async.MDCPropagatingExecutionContext


object ClientFactory {
  implicit val ec = MDCPropagatingExecutionContext.cachedNamedThreads("client-factory-thread-pool")

  lazy val webSocketClient = new ScalaWebSocketClient
  lazy val httpClient = new ScalaHttpClient
}
