package co.ledger.wallet.daemon.utils

import com.twitter.finagle.http.Request
import co.ledger.wallet.daemon.services.AuthenticationService.AuthentifiedUserContext._

class RichRequest(request: Request) {
  def user = request.user.get
}
