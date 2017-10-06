package co.ledger.wallet.daemon.models.coins

import java.util.Date

import co.ledger.core
import co.ledger.core.Operation
import co.ledger.wallet.daemon.models.CurrencyFamily
import co.ledger.wallet.daemon.utils
import co.ledger.wallet.daemon.utils.HexUtils
import com.fasterxml.jackson.annotation.JsonProperty

object Bitcoin extends Coin {

  override val currencyFamily = CurrencyFamily.BITCOIN

  override def getNetworkParamsView(from: core.Currency): NetworkParamsView = {
    val coreCrcyNetworkPrms = from.getBitcoinLikeNetworkParameters
    BitcoinNetworkParamsView(
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

//  override def getTransactionView(from: Operation): TransactionView = ???

}

case class BitcoinNetworkParamsView(
                                     @JsonProperty("identifier") identifier: String,
                                     @JsonProperty("p2pkh_version") p2pkhVersion: String,
                                     @JsonProperty("p2sh_version") p2shVersion: String,
                                     @JsonProperty("xpub_version") xpubVersion: String,
                                     @JsonProperty("fee_policy") feePolicy: String,
                                     @JsonProperty("dust_amount") dustAmount: Long,
                                     @JsonProperty("message_prefix") messagePrefix: String,
                                     @JsonProperty("uses_timestamped_transaction") usesTimeStampedTransaction: Boolean
                                   ) extends NetworkParamsView

//case class BitcoinTransactionView(
//                                 @JsonProperty("block") block: BitcoinBlockView,
//                                 @JsonProperty("fees") fees: Long,
//                                 @JsonProperty("hash") hash: String,
//                                 @JsonProperty("time") time: Date,
//                                 @JsonProperty("inputs") inputs: Seq[BitcoinInput],
//                                 @JsonProperty("lock_time") lockTime: Long,
//                                 @JsonProperty("outputs") outputs: Seq[BitcoinOutput]
//                                 ) extends TransactionView