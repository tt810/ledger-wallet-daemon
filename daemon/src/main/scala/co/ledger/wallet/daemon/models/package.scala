package co.ledger.wallet.daemon

import java.util.Date

import co.ledger.core
import co.ledger.wallet.daemon.utils.HexUtils
import com.fasterxml.jackson.annotation._
import co.ledger.core.implicits._
import org.joda.time.DateTime

import scala.collection.JavaConverters._
import scala.concurrent.{ExecutionContext, Future}

package object models {

  def newInstance(accountCreationInfo: core.AccountCreationInfo) = {
    AccountDerivationView(accountCreationInfo.getIndex,
      for {
        path <- accountCreationInfo.getDerivations.asScala.toList
        owner <- accountCreationInfo.getOwners.asScala.toList
      } yield DerivationView(path, owner, None, None))
  }

  def newInstance(account: core.Account, wallet: core.Wallet)(implicit ec: ExecutionContext): Future[AccountView] = {
    account.getBalance().map { balance =>
      AccountView(wallet.getName, account.getIndex, balance.toLong, "account key chain", newInstance(wallet.getCurrency))
    }
  }

  def newInstance(crCrcy: core.Currency): CurrencyView = {

    val currencyFamily = CurrencyFamily.valueOf(crCrcy.getWalletType.name())

    def networkParams: NetworkParamsView = currencyFamily match {
      case CurrencyFamily.BITCOIN => {
        val coreCrcyNetworkPrms = crCrcy.getBitcoinLikeNetworkParameters
        BitcoinLikeNetworkParamsView(
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

    def currencyUnit(coreCurrencyUnit: core.CurrencyUnit): UnitView =
      UnitView(
        coreCurrencyUnit.getName,
        coreCurrencyUnit.getSymbol,
        coreCurrencyUnit.getCode,
        coreCurrencyUnit.getNumberOfDecimal
      )

    CurrencyView(
      crCrcy.getName,
      currencyFamily,
      crCrcy.getBip44CoinType,
      crCrcy.getPaymentUriScheme,
      crCrcy.getUnits.asScala.toList.map(currencyUnit(_)),
      networkParams
    )
  }

  def newInstance(pool: core.WalletPool)(implicit ec: ExecutionContext): Future[WalletPoolView] =
    pool.getWalletCount().map(models.WalletPoolView(pool.getName, _))

  def newInstance(coreWallet: core.Wallet)(implicit ec: ExecutionContext): Future[WalletView] = {

    def getBalance(count: Int): Future[Long] = {
      coreWallet.getAccounts(0, count) flatMap { (accounts) =>
        val accs = accounts.asScala.toList
        val balances = Future.sequence(for (acc <- accs) yield acc.getBalance())
        balances.map { bs =>
          val bls = for(balance <- bs) yield balance.toLong
          utils.sum(bls)
        }
      }
    }

    val configuration: Map[String, Any] = {
      var configs = collection.mutable.Map[String, Any]()
      val dynamicObject = core.DynamicObject.newInstance()
      dynamicObject.getKeys.forEach { key =>
        configs += (key -> dynamicObject.getObject(key))
      }
      configs.toMap
    }

    coreWallet.getAccountCount().flatMap { count =>
      getBalance(count).map { balance =>
        WalletView(coreWallet.getName, count, balance, newInstance(coreWallet.getCurrency), configuration)
      }
    }
  }

  case class AccountView(
                        @JsonProperty("wallet_name") walletName: String,
                        @JsonProperty("index") index: Int,
                        @JsonProperty("balance") balance: Long,
                        @JsonProperty("keychain") keychain: String,
                        @JsonProperty("currency") currency: CurrencyView
                        )

  case class CurrencyView(
                         @JsonProperty("name") name: String,
                         @JsonProperty("family") family: CurrencyFamily,
                         @JsonProperty("bip_44_coin_type") bip44CoinType: Int,
                         @JsonProperty("payment_uri_scheme") paymentUriScheme: String,
                         @JsonProperty("units") units: Seq[UnitView],
                         @JsonProperty("network_params") networkParams: NetworkParamsView
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

  case class WalletPoolView(
                         @JsonProperty("name") name: String,
                         @JsonProperty("wallet_count") walletCount: Int
                         )

  case class UnitView(
                     @JsonProperty("name") name: String,
                     @JsonProperty("symbol") symbol: String,
                     @JsonProperty("code") code: String,
                     @JsonProperty("magnitude") magnitude: Int
                     )

  case class WalletView(
                       @JsonProperty("name") name: String,
                       @JsonProperty("account_count") accountCount: Int,
                       @JsonProperty("balance") balance: Long,
                       @JsonProperty("currency") currency: CurrencyView,
                       @JsonProperty("configuration") configuration: Map[String, Any]
                       )

  case class OperationView(
                          @JsonProperty("uid") uid: String,
                          @JsonProperty("currency_name") currencyName: String,
                          @JsonProperty("currency_family") currencyFamily: CurrencyFamily,
                          @JsonProperty("trust") trust: String,
                          @JsonProperty("confirmations") confirmations: Int,
                          @JsonProperty("time") time: Date,
                          @JsonProperty("block") block: Option[String],
                          @JsonProperty("type") opType: OperationType,
                          @JsonProperty("amount") amount: Long,
                          @JsonProperty("fees") fees: Long,
                          @JsonProperty("senders") senders: Seq[String],
                          @JsonProperty("recipients") recipients: Seq[String],
                          @JsonProperty("transaction") transaction: TransactionView
                          )

  case class PackedOperationsView(
                                 @JsonProperty("previous") previous: Option[String],
                                 @JsonProperty("next") next: Option[String],
                                 @JsonProperty("operations") operations: Seq[OperationView]
                                 )

  sealed trait NetworkParamsView

  sealed trait TransactionView

  case class BitcoinLikeNetworkParamsView(
                                         @JsonProperty("identifier") identifier: String,
                                         @JsonProperty("p2pkh_version") p2pkhVersion: String,
                                         @JsonProperty("p2sh_version") p2shVersion: String,
                                         @JsonProperty("xpub_version") xpubVersion: String,
                                         @JsonProperty("fee_policy") feePolicy: String,
                                         @JsonProperty("dust_amount") dustAmount: Long,
                                         @JsonProperty("message_prefix") messagePrefix: String,
                                         @JsonProperty("uses_timestamped_transaction") usesTimeStampedTransaction: Boolean
                                         ) extends NetworkParamsView

  case class Bulk(offset: Int = 0, bulkSize: Int = 20)

  case class WalletsViewWithCount(count: Int, wallets: Seq[WalletView])
}
