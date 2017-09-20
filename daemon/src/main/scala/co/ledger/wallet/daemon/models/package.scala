package co.ledger.wallet.daemon

import co.ledger.wallet.daemon.utils.HexUtils
import com.fasterxml.jackson.annotation.JsonProperty
import co.ledger.core.{BitcoinLikeNetworkParameters, CurrencyUnit, Account => CoreAccount, Currency => CoreCurrency, Wallet => CoreWallet, WalletPool => CoreWalletPool}
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

//  def newInstance(coreWallet: CoreWallet): Wallet =
//    Wallet(coreWallet.getName, _, getBalance(coreWallet), newInstance(coreWallet.getCurrency), getConfiguration(coreWallet))

  def newInstance(pool: CoreWalletPool): Future[WalletPool] =
    pool.getWalletCount().map(WalletPool(pool.getName, _))

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
                                     )

  case class Currency(
                     @JsonProperty("name") name: String,
                     @JsonProperty("family") family: String,
                     @JsonProperty("bip_44_coin_type") bip44CoinType: Int,
                     @JsonProperty("payment_uri_scheme") paymentUriScheme: String,
                     @JsonProperty("units") units: List[Unit],
                     @JsonProperty("network_params") networkParams: BitcoinLikeNetworkParams
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
                   @JsonProperty("account_count") accountCount: Int,
                   @JsonProperty("balance") balance: Long,
                   @JsonProperty("currency") currency: Currency,
                   @JsonProperty("configuration") configuration: Map[String, Any]
                   )



//  private  def getBalance(wallet: CoreWallet): Long = {
//
//    def sum(balances: Seq[Long]): Long = {
//      balances match {
//        case x :: tail => x + sum(tail)
//        case Nil => 0
//      }
//    }
//
//    val accounts: Future[List[CoreAccount]] = for (
//      count <- wallet.getAccountCount();
//      accountList <- wallet.getAccounts(0, count)
//    ) yield accountList.toList

//      accounts.flatMap { accountList =>
//        accountList.map { account =>
//          val amount: Future[Amount] = account.getBalance()
//          amount.flatMap(amount => amount.toLong)
//          account.getBalance() flatMap { amount => amount.toLong() }
//        }
//      }
//    0
//  }
//
//   private def getConfiguration(wallet: CoreWallet): Map[String, Any] = {
//    Map[String, Any]()
//  }


}
