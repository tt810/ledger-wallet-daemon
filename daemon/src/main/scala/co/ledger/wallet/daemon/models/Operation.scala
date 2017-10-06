package co.ledger.wallet.daemon.models

import java.util.Date

import co.ledger.core
import co.ledger.wallet.daemon.models.coins.TransactionView

import scala.collection.JavaConverters._
import com.fasterxml.jackson.annotation.JsonInclude.Include
import com.fasterxml.jackson.annotation.{JsonInclude, JsonProperty}

object Operation {

  def newView(operation: core.Operation, currencyName: String) = {
    val currencyFamily = CurrencyFamily.valueOf(operation.getWalletType.name())

    val uid = operation.getUid

    val trust = operation.getTrust
    val confirmations = 0 //TODO
    val date = operation.getDate
    val block = operation.getBlockHeight  //?
    val opType = OperationType.valueOf(operation.getOperationType.name())
    val amount = operation.getAmount.toLong
    val fees = operation.getFees.toLong
    val senders = operation.getSenders.asScala.toSeq
    val recipients = operation.getRecipients.asScala.toSeq
    //    val transaction = (if (operation.isComplete)
    //                        else null)
    operation.asBitcoinLikeOperation().getTransaction.getInputs
    // val transaction = operation.

  }


}

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
                          @JsonProperty("wallet_name") walletName: String,
                          @JsonProperty("account_index") accountIndex: Int,
                          @JsonProperty("senders") senders: Seq[String],
                          @JsonProperty("recipients") recipients: Seq[String],
                          @JsonProperty("transaction") @JsonInclude(Include.NON_NULL) transaction: TransactionView
                        )

case class PackedOperationsView(
                                 @JsonProperty("previous") previous: Option[String],
                                 @JsonProperty("next") next: Option[String],
                                 @JsonProperty("operations") operations: Seq[OperationView]
                               )
