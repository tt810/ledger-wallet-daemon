package co.ledger.wallet.daemon.controllers.requests

import com.twitter.finatra.validation.ValidationResult

object CommonMethodValidations {

  def validateOptionalAccountIndex(accountIndex: Option[Int]): ValidationResult =
    ValidationResult.validate(accountIndex.isEmpty || accountIndex.get >= 0, s"account_index: index can not be less than zero")

  def validateName(name: String, nameStr: String): ValidationResult = {
    ValidationResult.validate(
      REGEX.pattern.matcher(nameStr).matches,
      s"$name: invalid $name, $name should match ${REGEX.toString()}")
  }

  private val REGEX = "([0-9a-zA-Z]+[_]?)+[0-9a-zA-Z]+".r
}
