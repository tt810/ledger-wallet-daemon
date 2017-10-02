package co.ledger.wallet.daemon.controllers


import co.ledger.core.LedgerCore
import co.ledger.wallet.daemon.services.LogMsgMaker
import com.twitter.finagle.http.Request
import com.twitter.finatra.http.Controller

class StatusController extends Controller {
  import StatusController._

  get("/status") {(request: Request) =>
    info(LogMsgMaker.newInstance("GET status request")
      .append("request", request)
      .toString())
    response.ok(Status(LedgerCore.getStringVersion))
  }
}

object StatusController {
  case class Status(engine_version: String)
}