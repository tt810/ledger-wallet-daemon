package co.ledger.wallet.daemon.controllers

import javax.inject.Inject

import co.ledger.core.LedgerCore
import co.ledger.wallet.daemon.services.DatabaseService
import co.ledger.wallet.daemon.swagger.DocumentedController
import com.jakehschwartz.finatra.swagger.SwaggerController
import com.twitter.finagle.http.{Request, Response}
import com.twitter.finatra.http.Controller
import io.swagger.annotations.{ApiModel, ApiModelProperty}
import io.swagger.models.{SecurityRequirement, Swagger}

class StatusController @Inject()(db: DatabaseService, s: Swagger) extends SwaggerController {
  import StatusController._
  override protected implicit val swagger: Swagger = s


  getWithDoc("/status") {o =>
    o .summary("Retrieves the current status and a bunch information about the running wallet manager.")
      .tag("Status API")
        .addSecurity("DemoAuth", List("*"))
      .responseWith[Status](200, "The current status of the application")
  } {(request: Request) =>
    response.ok(Status(LedgerCore.getStringVersion))
  }
}

object StatusController {
  @ApiModel(value = "Status", description = "Display the current status of the application, the version of the database " +
    "and the version of the libledger-core engine.")
  case class Status(
                     @ApiModelProperty(value = "The current version of the libledger-core used by the daemon")
                     engine_version: String
                   )
}