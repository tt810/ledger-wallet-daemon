package co.ledger.wallet.protocol

import scala.concurrent.Future

trait LedgerCoreApi {
  def getLibraryVersion(): Future[String]
}
