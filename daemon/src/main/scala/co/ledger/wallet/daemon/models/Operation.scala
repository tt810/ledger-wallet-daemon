package co.ledger.wallet.daemon.models

import java.util.{Date, UUID}

import co.ledger.core
import co.ledger.wallet.daemon.models.Account.Account
import co.ledger.wallet.daemon.models.coins.Bitcoin
import co.ledger.wallet.daemon.models.coins.Coin.TransactionView
import com.fasterxml.jackson.annotation.JsonInclude.Include
import com.fasterxml.jackson.annotation.{JsonInclude, JsonProperty}

import scala.collection.JavaConverters._
import scala.concurrent.Future

class Operation(private val coreO: core.Operation, private val coreA: core.Account, private val coreW: core.Wallet) extends Account(coreA, coreW) {

  def operationView: Future[OperationView] = {

    val currencyFamily = CurrencyFamily.valueOf(coreO.getWalletType.name())
    val uid = coreO.getUid
    val trust = newTrustIndicatorView(coreO.getTrust)
    val time = coreO.getDate
    val opType = OperationType.valueOf(coreO.getOperationType.name())
    val amount = coreO.getAmount.toLong
    val fees = coreO.getFees.toLong
    val senders = coreO.getSenders.asScala.toSeq
    val recipients = coreO.getRecipients.asScala.toSeq
    val transaction = newTransactionView(coreO, currencyFamily)
    confirmations.map(OperationView(uid, currency.currencyName, currencyFamily, trust, _, time, blockHeight, opType, amount, fees, walletName, accountIndex, senders, recipients, transaction))
  }

  private def confirmations: Future[Long] = {
    if (blockHeight < 0) initialBlockHeight.map { _ => blockHeight - coreO.getBlockHeight }
    else Future.successful(blockHeight - coreO.getBlockHeight)
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

  def newInstance(coreO: core.Operation, coreA: core.Account, coreW: core.Wallet): Operation = {
    new Operation(coreO, coreA, coreW)
  }

}

case class OperationView(
                          @JsonProperty("uid") uid: String,
                          @JsonProperty("currency_name") currencyName: String,
                          @JsonProperty("currency_family") currencyFamily: CurrencyFamily,
                          @JsonProperty("trust") trust: Option[TrustIndicatorView],
                          @JsonProperty("confirmations") confirmations: Long,
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
