package co.ledger.wallet.daemon.models

import co.ledger.core
import co.ledger.wallet.daemon.models.coins.Coin.NetworkParamsView
import co.ledger.wallet.daemon.models.coins.Bitcoin
import com.fasterxml.jackson.annotation.JsonProperty

import scala.collection.JavaConverters._

class Currency(coreC: core.Currency) {

  val currencyName = coreC.getName

  val currencyFamily = CurrencyFamily.valueOf(coreC.getWalletType.name())

  lazy val currencyView = CurrencyView(
    coreC.getName,
    currencyFamily,
    coreC.getBip44CoinType,
    coreC.getPaymentUriScheme,
    coreC.getUnits.asScala.toSeq.map(newUnitView(_)),
    newNetworkParamsView(coreC, currencyFamily)
  )

  private def newUnitView(coreUnit: core.CurrencyUnit): UnitView =
    UnitView(coreUnit.getName, coreUnit.getSymbol, coreUnit.getCode, coreUnit.getNumberOfDecimal)

  private def newNetworkParamsView(coreCurrency: core.Currency, currencyFamily: CurrencyFamily): NetworkParamsView = currencyFamily match {
    case CurrencyFamily.BITCOIN => Bitcoin.newNetworkParamsView(coreCurrency.getBitcoinLikeNetworkParameters)
    case _ => ???
  }

  override def equals(that: Any): Boolean = {
    that match {
      case that: Currency => that.isInstanceOf[Currency] && this.hashCode == that.hashCode
      case _ => false
    }
  }

  override def hashCode: Int = {
    this.currencyName.hashCode + this.currencyFamily.hashCode()
  }

  override def toString: String = s"Currency(name: $currencyName, family: $currencyFamily)"
}

object Currency {
  def newInstance(coreC: core.Currency): Currency = {
    new Currency(coreC)
  }
}

case class CurrencyView(
                         @JsonProperty("name") name: String,
                         @JsonProperty("family") family: CurrencyFamily,
                         @JsonProperty("bip_44_coin_type") bip44CoinType: Int,
                         @JsonProperty("payment_uri_scheme") paymentUriScheme: String,
                         @JsonProperty("units") units: Seq[UnitView],
                         @JsonProperty("network_params") networkParams: NetworkParamsView
                       )

case class UnitView(
                     @JsonProperty("name") name: String,
                     @JsonProperty("symbol") symbol: String,
                     @JsonProperty("code") code: String,
                     @JsonProperty("magnitude") magnitude: Int
                   )