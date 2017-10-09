package co.ledger.wallet.daemon.models.coins

import java.util.Date

import co.ledger.core
import co.ledger.wallet.daemon.models.CurrencyFamily
import co.ledger.wallet.daemon.models.coins.Coin._
import co.ledger.wallet.daemon.utils
import co.ledger.wallet.daemon.utils.HexUtils
import com.fasterxml.jackson.annotation.JsonProperty
import scala.collection.JavaConverters._

object Bitcoin {

  val currencyFamily = CurrencyFamily.BITCOIN

  def newNetworkParamsView(from: core.BitcoinLikeNetworkParameters): NetworkParamsView = {
    BitcoinNetworkParamsView(
      from.getIdentifier,
      utils.HexUtils.valueOf(from.getP2PKHVersion),
      HexUtils.valueOf(from.getP2SHVersion),
      HexUtils.valueOf(from.getXPUBVersion),
      from.getFeePolicy.name,
      from.getDustAmount,
      from.getMessagePrefix,
      from.getUsesTimestampedTransaction
    )
  }

  def newTransactionView(from: core.BitcoinLikeTransaction): TransactionView = {
    BitcoinTransactionView(
      newBlockView(from.getBlock),
      if(from.getFees == null) None else Option(from.getFees.toLong),
      from.getHash,
      from.geTime(),
      from.getInputs.asScala.toSeq.map(newInputView(_)),
      from.getLockTime,
      from.getOutputs.asScala.toSeq.map(newOutputView(_))
    )
  }

  private def newBlockView(from: core.BitcoinLikeBlock): BlockView = {
    BitcoinBlockView(from.getHash, from.getHeight, from.getTime)
  }

  private def newInputView(from: core.BitcoinLikeInput): InputView = {
    BitcoinInputView(from.getAddress, from.getValue.toLong, Option(from.getCoinbase), Option(from.getPreviousTxHash), Option(from.getPreviousOutputIndex))
  }

  private def newOutputView(from: core.BitcoinLikeOutput): OutputView = {
    BitcoinOutputView(from.getAddress, from.getTransactionHash, from.getOutputIndex, from.getValue.toLong, HexUtils.valueOf(from.getScript))
  }
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

case class BitcoinTransactionView(
                                   @JsonProperty("block") block: BlockView,
                                   @JsonProperty("fees") fees: Option[Long],
                                   @JsonProperty("hash") hash: String,
                                   @JsonProperty("time") time: Date,
                                   @JsonProperty("inputs") inputs: Seq[InputView],
                                   @JsonProperty("lock_time") lockTime: Long,
                                   @JsonProperty("outputs") outputs: Seq[OutputView]
                                 ) extends TransactionView

case class BitcoinBlockView(
                           @JsonProperty("hash") hash: String,
                           @JsonProperty("height") height: Long,
                           @JsonProperty("time") time: Date
                           ) extends BlockView

case class BitcoinInputView(
                           @JsonProperty("address") address: String,
                           @JsonProperty("value") value: Long,
                           @JsonProperty("coinbase") coinbase: Option[String],
                           @JsonProperty("previous_transaction_hash") previousTxHash: Option[String],
                           @JsonProperty("previous_output_index") previousOutputIndex: Option[Int]
                           ) extends InputView

case class BitcoinOutputView(
                            @JsonProperty("address") address: String,
                            @JsonProperty("transaction_hash") txHash: String,
                            @JsonProperty("output_index") outputIndex: Int,
                            @JsonProperty("value") value: Long,
                            @JsonProperty("script") script: String
                            ) extends OutputView