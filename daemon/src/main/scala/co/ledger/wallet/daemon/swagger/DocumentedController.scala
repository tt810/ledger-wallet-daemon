package co.ledger.wallet.daemon.swagger

import co.ledger.wallet.daemon.Server
import com.jakehschwartz.finatra.swagger.SwaggerController
import com.twitter.finagle.http.RouteIndex
import io.swagger.models.{Operation, Swagger}

trait DocumentedController extends SwaggerController {
  override protected implicit val swagger: Swagger = Server.swagger

}
