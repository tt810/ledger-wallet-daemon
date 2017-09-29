package co.ledger.wallet.daemon

import co.ledger.core.{CurrencyUnit, Account => CoreAccount, Currency => CoreCurrency, Wallet => CoreWallet, WalletPool => CoreWalletPool}
import co.ledger.wallet.daemon.utils.HexUtils
import com.fasterxml.jackson.annotation._
import co.ledger.core.implicits._

import scala.collection.JavaConverters._
import scala.concurrent.{ExecutionContext, Future}

package object models {

  def newInstance(crcyFamily: CurrencyFamily, coreCurrency: CoreCurrency): NetworkParams = crcyFamily match {
    case CurrencyFamily.BITCOIN => {
      val coreCrcyNetworkPrms = coreCurrency.getBitcoinLikeNetworkParameters
      BitcoinLikeNetworkParams(
        coreCrcyNetworkPrms.getIdentifier,
        utils.HexUtils.valueOf(coreCrcyNetworkPrms.getP2PKHVersion),
        HexUtils.valueOf(coreCrcyNetworkPrms.getP2SHVersion),
        HexUtils.valueOf(coreCrcyNetworkPrms.getXPUBVersion),
        coreCrcyNetworkPrms.getFeePolicy.name,
        coreCrcyNetworkPrms.getDustAmount,
        coreCrcyNetworkPrms.getMessagePrefix,
        coreCrcyNetworkPrms.getUsesTimestampedTransaction
      )
    }
    case _ => ???
  }

//  def newInstance(account: CoreAccount, walletName: String): Account = {
//    account.getBalance().map { balance =>
//      Account(walletName, account.getIndex, balance, account.get)
//    }
//
//  }

  def newInstance(crCrcy: CoreCurrency): Currency = {
    val currencyFamily = CurrencyFamily.valueOf(crCrcy.getWalletType.name())
    Currency(
      crCrcy.getName,
      currencyFamily,
      crCrcy.getBip44CoinType,
      crCrcy.getPaymentUriScheme,
      crCrcy.getUnits.asScala.toList.map(newInstance(_)),
      newInstance(currencyFamily, crCrcy)
    )
  }

  def newInstance(pool: CoreWalletPool)(implicit ec: ExecutionContext): Future[WalletPool] =
    pool.getWalletCount().map(models.WalletPool(pool.getName, _))

  def newInstance(coreWallet: CoreWallet): Unit = ???
//  {
//    Wallet(coreWallet.getName, newInstance(coreWallet.getCurrency), coreWallet.getAccountCount(), core)
//  }

  def newInstance(coreCurrencyUnit: CurrencyUnit): Unit =
    Unit(
      coreCurrencyUnit.getName,
      coreCurrencyUnit.getSymbol,
      coreCurrencyUnit.getCode,
      coreCurrencyUnit.getNumberOfDecimal
    )

  case class Account(
                      @JsonProperty("wallet_name") walletName: String,
                      @JsonProperty("index") index: Int,
                      @JsonProperty("balance") balance: Long,
                      @JsonProperty("keychain") keychain: String,
                      @JsonProperty("currency") currency: Currency
                    )

  case class BitcoinLikeNetworkParams(
                                       @JsonProperty("identifier") identifier: String,
                                       @JsonProperty("p2pkh_version") p2pkhVersion: String,
                                       @JsonProperty("p2sh_version") p2shVersion: String,
                                       @JsonProperty("xpub_version") xpubVersion: String,
                                       @JsonProperty("fee_policy") feePolicy: String,
                                       @JsonProperty("dust_amount") dustAmount: Long,
                                       @JsonProperty("message_prefix") messagePrefix: String,
                                       @JsonProperty("uses_timestamped_transaction") usesTimeStampedTransaction: Boolean
                                     ) extends NetworkParams

  case class Currency(
                       @JsonProperty("name") name: String,
                       @JsonIgnore family: CurrencyFamily,
                       @JsonProperty("bip_44_coin_type") bip44CoinType: Int,
                       @JsonProperty("payment_uri_scheme") paymentUriScheme: String,
                       @JsonProperty("units") units: Seq[Unit],
                       @JsonProperty("network_params") networkParams: NetworkParams
                     )

  case class Derivation(
                       @JsonProperty("path") path: String,
                       @JsonProperty("owner") owner: String,
                       @JsonProperty("pub_key") pubKey: Option[String],
                       @JsonProperty("chain_code") chainCode: Option[String]
                       )

  case class AccountDerivation(
                              @JsonProperty("account_index") accountIndex: Int,
                              @JsonProperty("derivations") derivations: Seq[Derivation]
                              )

  case class WalletPool(
                       @JsonProperty("name") name: String,
                       @JsonProperty("wallet_count") walletCount: Int
                       )

  case class Unit(
                   @JsonProperty("name") name: String,
                   @JsonProperty("symbol") symbol: String,
                   @JsonProperty("code") code: String,
                   @JsonProperty("magnitude") magnitude: Int
                 )

  case class Wallet(
                     @JsonProperty("name") name: String,
                     @JsonProperty("currency") currency: Currency,
                     @JsonProperty("account_count") accountCount: Int,
                     @JsonProperty("balance") balance: Long,
                     @JsonProperty("configuration") configuration: Map[String, Any]
                   )

  trait NetworkParams

}
