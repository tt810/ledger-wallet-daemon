package co.ledger.wallet.daemon.controllers

import javax.inject.Inject

import co.ledger.core.LedgerCore
import co.ledger.wallet.daemon.services.DatabaseService
import com.twitter.finagle.http.Request
import com.twitter.finatra.http.Controller

class StatusController @Inject()(db: DatabaseService) extends Controller {
  import StatusController._

  get("/status") {(request: Request) =>
    response.ok(Status(LedgerCore.getStringVersion))
  }
}

object StatusController {
  case class Status(engine_version: String)
}