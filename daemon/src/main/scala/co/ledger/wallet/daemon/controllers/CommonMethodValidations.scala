package co.ledger.wallet.daemon.controllers

import co.ledger.wallet.daemon.database.DefaultDaemonCache
import com.twitter.finatra.validation.ValidationResult

object CommonMethodValidations {

  def validateOptionalAccountIndex(accountIndex: Option[Int]) =
    ValidationResult.validate(accountIndex.isEmpty || accountIndex.get >= 0, s"account_index: index can not be less than zero")


  def validateName(name: String, nameStr: String): ValidationResult = {
    ValidationResult.validate(
      regex.pattern.matcher(nameStr).matches,
      s"$name: invalid $name, $name should match ${regex.toString()}")
  }

  private val regex = "([0-9a-zA-Z]+[_]?)+[0-9a-zA-Z]+".r

  object DaemonCacheInstance extends DefaultDaemonCache
}
