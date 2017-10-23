package co.ledger.wallet.daemon.models

import java.util.{Date, UUID}

import co.ledger.core
import co.ledger.wallet.daemon.models.coins.Bitcoin
import co.ledger.wallet.daemon.models.coins.Coin.TransactionView

import scala.collection.JavaConverters._
import com.fasterxml.jackson.annotation.JsonInclude.Include
import com.fasterxml.jackson.annotation.{JsonInclude, JsonProperty}

class Operation(private val coreO: core.Operation, private val coreC: core.Currency) extends Currency(coreC) {

  def toView(walletName: String, accountIndex: Int): OperationView = {

    val currencyFamily = CurrencyFamily.valueOf(coreO.getWalletType.name())
    val uid = coreO.getUid
    val trust = newTrustIndicatorView(coreO.getTrust)
    val confirmations = 0 //TODO
    val time = coreO.getDate
    val blockHeight = coreO.getBlockHeight  //?
    val opType = OperationType.valueOf(coreO.getOperationType.name())
    val amount = coreO.getAmount.toLong
    val fees = coreO.getFees.toLong
    val senders = coreO.getSenders.asScala.toSeq
    val recipients = coreO.getRecipients.asScala.toSeq
    val transaction = newTransactionView(coreO, currencyFamily)
    OperationView(uid, currencyName, currencyFamily, trust, confirmations, time, blockHeight, opType, amount, fees, walletName, accountIndex, senders, recipients, transaction)
  }

  private def newTransactionView(operation: core.Operation, currencyFamily: CurrencyFamily) = {
    if(operation.isComplete) currencyFamily match {
      case CurrencyFamily.BITCOIN => Bitcoin.newTransactionView(operation.asBitcoinLikeOperation().getTransaction)
      case _ => ???
    }
    else null
  }

  private def newTrustIndicatorView(trust: core.TrustIndicator): Option[TrustIndicatorView] = {
    if (trust == null) None
    else
      Option(TrustIndicatorView(trust.getTrustWeight, TrustLevel.valueOf(trust.getTrustLevel.name()), trust.getConflictingOperationUids.asScala.toSeq, trust.getOrigin))
  }
}

object Operation {

  def newInstance(coreO: core.Operation, coreC: core.Currency): Operation = {
    new Operation(coreO, coreC)
  }

}

case class OperationView(
                          @JsonProperty("uid") uid: String,
                          @JsonProperty("currency_name") currencyName: String,
                          @JsonProperty("currency_family") currencyFamily: CurrencyFamily,
                          @JsonProperty("trust") trust: Option[TrustIndicatorView],
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
                                 @JsonProperty("previous") previous: Option[UUID],
                                 @JsonProperty("next") next: Option[UUID],
                                 @JsonProperty("operations") operations: Seq[OperationView]
                               )
