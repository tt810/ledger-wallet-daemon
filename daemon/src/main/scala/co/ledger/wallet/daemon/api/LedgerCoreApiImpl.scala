package co.ledger.wallet.daemon.api

import co.ledger.core.LedgerCore
import co.ledger.wallet.protocol.LedgerCoreApi

import scala.concurrent.Future

class LedgerCoreApiImpl extends LedgerCoreApi {
  override def getLibraryVersion(): Future[String] = {
    Future.successful(LedgerCore.getStringVersion)
  }
}
