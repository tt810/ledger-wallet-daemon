package co.ledger.wallet.daemon

import co.ledger.core
import co.ledger.wallet.daemon.utils.HexUtils
import com.fasterxml.jackson.annotation._
import co.ledger.core.implicits._

import scala.collection.JavaConverters._
import scala.concurrent.{ExecutionContext, Future}

package object models {

  def newInstance(accountCreationInfo: core.AccountCreationInfo) = {
    AccountDerivation(accountCreationInfo.getIndex,
      for {
        path <- accountCreationInfo.getDerivations.asScala.toList
        owner <- accountCreationInfo.getOwners.asScala.toList
      } yield Derivation(path, owner, None, None))
  }

  def newInstance(account: core.Account, wallet: core.Wallet)(implicit ec: ExecutionContext): Future[Account] = {
    account.getBalance().map { balance =>
      Account(wallet.getName, account.getIndex, balance.toLong, "account key chain", newInstance(wallet.getCurrency))
    }
  }

  def newInstance(crCrcy: core.Currency): Currency = {

    val currencyFamily = CurrencyFamily.valueOf(crCrcy.getWalletType.name())

    def networkParams: NetworkParams = currencyFamily match {
      case CurrencyFamily.BITCOIN => {
        val coreCrcyNetworkPrms = crCrcy.getBitcoinLikeNetworkParameters
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

    def currencyUnit(coreCurrencyUnit: core.CurrencyUnit): CurrencyUnit =
      CurrencyUnit(
        coreCurrencyUnit.getName,
        coreCurrencyUnit.getSymbol,
        coreCurrencyUnit.getCode,
        coreCurrencyUnit.getNumberOfDecimal
      )

    Currency(
      crCrcy.getName,
      currencyFamily,
      crCrcy.getBip44CoinType,
      crCrcy.getPaymentUriScheme,
      crCrcy.getUnits.asScala.toList.map(currencyUnit(_)),
      networkParams
    )
  }

  def newInstance(pool: core.WalletPool)(implicit ec: ExecutionContext): Future[WalletPool] =
    pool.getWalletCount().map(models.WalletPool(pool.getName, _))

  def newInstance(coreWallet: core.Wallet)(implicit ec: ExecutionContext): Future[Wallet] = {

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
        Wallet(coreWallet.getName, count, balance, newInstance(coreWallet.getCurrency), configuration)
      }
    }
  }

  case class Account(
                      @JsonProperty("wallet_name") walletName: String,
                      @JsonProperty("index") index: Int,
                      @JsonProperty("balance") balance: Long,
                      @JsonProperty("keychain") keychain: String,
                      @JsonProperty("currency") currency: Currency
                    )

  case class Currency(
                       @JsonProperty("name") name: String,
                       @JsonProperty("family") family: CurrencyFamily,
                       @JsonProperty("bip_44_coin_type") bip44CoinType: Int,
                       @JsonProperty("payment_uri_scheme") paymentUriScheme: String,
                       @JsonProperty("units") units: Seq[CurrencyUnit],
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

  case class CurrencyUnit(
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

  sealed trait NetworkParams

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

  case class Bulk(offset: Int = 0, bulkSize: Int = 20)

  case class WalletsWithCount(count: Int, wallets: Seq[Wallet])
}
