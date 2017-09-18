package co.ledger.wallet.daemon

import co.ledger.core.WalletPool
import co.ledger.wallet.daemon.utils.HexUtils
import com.fasterxml.jackson.annotation.JsonProperty
import co.ledger.core.{BitcoinLikeNetworkParameters, CurrencyUnit, Currency => CoreCurrency}
import co.ledger.core.implicits._

import scala.collection.JavaConverters._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

package object models {

  def newInstance(coreCrcyNetworkPrms: BitcoinLikeNetworkParameters): BitcoinLikeNetworkParams =
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

  def newInstance(crCrcy: CoreCurrency): Currency =
    Currency(
      crCrcy.getName,
      crCrcy.getWalletType.name,
      crCrcy.getBip44CoinType,
      crCrcy.getPaymentUriScheme,
      crCrcy.getUnits.asScala.toList.map(newInstance(_)),
      newInstance(crCrcy.getBitcoinLikeNetworkParameters)
    )

  def newInstance(pool: WalletPool): Future[Pool] = pool.getWalletCount().map(models.Pool(pool.getName, _))

  def newInstance(coreCurrencyUnit: CurrencyUnit): Unit =
    Unit(
      coreCurrencyUnit.getName,
      coreCurrencyUnit.getSymbol,
      coreCurrencyUnit.getCode,
      coreCurrencyUnit.getNumberOfDecimal
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
                                     )

  case class Currency(
                     @JsonProperty("name") name: String,
                     @JsonProperty("family") family: String,
                     @JsonProperty("bip_44_coin_type") bip44CoinType: Int,
                     @JsonProperty("payment_uri_scheme") paymentUriScheme: String,
                     @JsonProperty("units") units: List[Unit],
                     @JsonProperty("network_params") networkParams: BitcoinLikeNetworkParams
                     )

  case class Pool(
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
                   @JsonProperty("account_count") accountCount: Int,
                   @JsonProperty("balance") balance: Long,
                   @JsonProperty("currency") currency: Currency
                   //@JsonProperty("configuration") configuration: Map[String, Any]
                   )

}
