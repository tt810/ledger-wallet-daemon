package co.ledger.wallet.daemon.models

import co.ledger.core
import co.ledger.core.implicits._
import com.fasterxml.jackson.annotation.JsonProperty
import scala.collection.JavaConverters._
import scala.concurrent.{ExecutionContext, Future}

object Account {

  def newView(account: core.Account, wallet: core.Wallet)(implicit ec: ExecutionContext): Future[AccountView] = {
    account.getBalance().map { balance =>
      AccountView(wallet.getName, account.getIndex, balance.toLong, "account key chain", Currency.newView(wallet.getCurrency))
    }
  }

  def newDerivationView(accountCreationInfo: core.AccountCreationInfo): AccountDerivationView = {
    AccountDerivationView(accountCreationInfo.getIndex,
      for {
        path <- accountCreationInfo.getDerivations.asScala.toList
        owner <- accountCreationInfo.getOwners.asScala.toList
      } yield DerivationView(path, owner, None, None))
  }

}

case class AccountView(
                        @JsonProperty("wallet_name") walletName: String,
                        @JsonProperty("index") index: Int,
                        @JsonProperty("balance") balance: Long,
                        @JsonProperty("keychain") keychain: String,
                        @JsonProperty("currency") currency: CurrencyView
                      )

case class DerivationView(
                           @JsonProperty("path") path: String,
                           @JsonProperty("owner") owner: String,
                           @JsonProperty("pub_key") pubKey: Option[String],
                           @JsonProperty("chain_code") chainCode: Option[String]
                         )

case class AccountDerivationView(
                                  @JsonProperty("account_index") accountIndex: Int,
                                  @JsonProperty("derivations") derivations: Seq[DerivationView]
                                )