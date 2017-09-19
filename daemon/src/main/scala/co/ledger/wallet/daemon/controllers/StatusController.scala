package co.ledger.wallet.daemon.controllers


import co.ledger.core.LedgerCore
import com.twitter.finagle.http.Request
import com.twitter.finatra.http.Controller

class StatusController extends Controller {
  import StatusController._

  get("/status") {(request: Request) =>
    response.ok(Status(LedgerCore.getStringVersion))
  }
}

object StatusController {
  case class Status(engine_version: String)
}