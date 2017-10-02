package co.ledger.wallet.daemon.controllers

import co.ledger.wallet.daemon.database.DefaultDaemonCache
import com.twitter.finatra.validation.ValidationResult

object CommonMethodValidations {

  def validateName(name: String, nameStr: String) = {
    ValidationResult.validate(
      regex.pattern.matcher(nameStr).matches,
      s"$name: invalid $name, $name should match ${regex.toString()}")
  }

  private val regex = "([0-9a-zA-Z]+[_]?)+[0-9a-zA-Z]+".r

  object DaemonCacheInstance extends DefaultDaemonCache
}
