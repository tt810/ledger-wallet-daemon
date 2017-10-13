package co.ledger.wallet.daemon.clients


object ClientFactory {
  lazy val webSocketClient = new ScalaWebSocketClient
}
