package co.ledger.wallet.daemon.models

import java.util.Date

import co.ledger.core
import co.ledger.wallet.daemon.models.coins.Bitcoin
import co.ledger.wallet.daemon.models.coins.Coin.TransactionView

import scala.collection.JavaConverters._
import com.fasterxml.jackson.annotation.JsonInclude.Include
import com.fasterxml.jackson.annotation.{JsonInclude, JsonProperty}

object Operation {

  def newView(operation: core.Operation, currencyName: String, walletName: String, accountIndex: Int) = {
    val currencyFamily = CurrencyFamily.valueOf(operation.getWalletType.name())

    val uid = operation.getUid

//    val trust = newTrustIndicatorView(operation.getTrust)
    val confirmations = 0 //TODO
    val time = operation.getDate
    val blockHeight = operation.getBlockHeight  //?
    val opType = OperationType.valueOf(operation.getOperationType.name())
    val amount = operation.getAmount.toLong
    val fees = operation.getFees.toLong
    val senders = operation.getSenders.asScala.toSeq
    val recipients = operation.getRecipients.asScala.toSeq
    val transaction = newTransactionView(operation, currencyFamily)
    OperationView(uid, currencyName, currencyFamily, null, confirmations, time, blockHeight, opType, amount, fees, walletName, accountIndex, senders, recipients, transaction)

  }

  private def newTransactionView(operation: core.Operation, currencyFamily: CurrencyFamily) = {
    if(operation.isComplete) currencyFamily match {
      case CurrencyFamily.BITCOIN => Bitcoin.newTransactionView(operation.asBitcoinLikeOperation().getTransaction)
      case _ => ???
    }
    else null
  }

  private def newTrustIndicatorView(trust: core.TrustIndicator): TrustIndicatorView = {
    TrustIndicatorView(trust.getTrustWeight, TrustLevel.valueOf(trust.getTrustLevel.name()), trust.getConflictingOperationUids.asScala.toSeq, trust.getOrigin)
  }

}

case class OperationView(
                          @JsonProperty("uid") uid: String,
                          @JsonProperty("currency_name") currencyName: String,
                          @JsonProperty("currency_family") currencyFamily: CurrencyFamily,
                          @JsonProperty("trust") trust: TrustIndicatorView,
                          @JsonProperty("confirmations") confirmations: Int,
                          @JsonProperty("time") time: Date,
                          @JsonProperty("block_height") blockHeight: Long,
                          @JsonProperty("type") opType: OperationType,
                          @JsonProperty("amount") amount: Long,
                          @JsonProperty("fees") fees: Long,
                          @JsonProperty("wallet_name") walletName: String,
                          @JsonProperty("account_index") accountIndex: Int,
                          @JsonProperty("senders") senders: Seq[String],
                          @JsonProperty("recipients") recipients: Seq[String],
                          @JsonProperty("transaction") @JsonInclude(Include.NON_NULL) transaction: TransactionView
                        )

case class TrustIndicatorView(
                             @JsonProperty("weight") weight: Int,
                             @JsonProperty("level") level: TrustLevel,
                             @JsonProperty("conflicted_operations") conflictedOps: Seq[String],
                             @JsonProperty("origin") origin: String
                             )

case class PackedOperationsView(
                                 @JsonProperty("previous") previous: Option[String],
                                 @JsonProperty("next") next: Option[String],
                                 @JsonProperty("operations") operations: Seq[OperationView]
                               )
