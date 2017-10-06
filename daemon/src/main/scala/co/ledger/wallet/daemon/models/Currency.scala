package co.ledger.wallet.daemon.models

import co.ledger.core
import co.ledger.wallet.daemon.models.coins.Coin.NetworkParamsView
import co.ledger.wallet.daemon.models.coins.Bitcoin
import com.fasterxml.jackson.annotation.JsonProperty

import scala.collection.JavaConverters._

object Currency {

  def newView(crCrcy: core.Currency): CurrencyView = {

    val currencyFamily = CurrencyFamily.valueOf(crCrcy.getWalletType.name())

    CurrencyView(
      crCrcy.getName,
      currencyFamily,
      crCrcy.getBip44CoinType,
      crCrcy.getPaymentUriScheme,
      crCrcy.getUnits.asScala.toList.map(newUnitView(_)),
      newNetworkParamsView(crCrcy, currencyFamily)
    )
  }

  private def newUnitView(coreUnit: core.CurrencyUnit): UnitView =
    UnitView(coreUnit.getName, coreUnit.getSymbol, coreUnit.getCode, coreUnit.getNumberOfDecimal)

  private def newNetworkParamsView(coreCurrency: core.Currency, currencyFamily: CurrencyFamily): NetworkParamsView = currencyFamily match {
    case CurrencyFamily.BITCOIN => Bitcoin.newNetworkParamsView(coreCurrency.getBitcoinLikeNetworkParameters)
    case _ => ???
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