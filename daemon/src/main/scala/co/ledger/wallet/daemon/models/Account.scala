package co.ledger.wallet.daemon.models

import co.ledger.core
import co.ledger.core.implicits._
import co.ledger.wallet.daemon.database.{SynchronizationEventReceiver, SynchronizationResult}
import com.fasterxml.jackson.annotation.JsonProperty

import scala.collection.JavaConverters._
import scala.concurrent.{ExecutionContext, Future, Promise}

object Account {

  class Account(private val coreA: core.Account, private val coreC: core.Currency)(implicit ec: ExecutionContext) extends Currency(coreC){

    val index = coreA.getIndex

    def toView(walletName: String): Future[AccountView] = coreA.getBalance().map { balance =>
      AccountView(walletName, index, balance.toLong, "account key chain", currencyView)
    }

    def operations(offset: Long, batch: Int, fullOp: Int): Future[Seq[Operation]] = {
      (if (fullOp > 0)
        coreA.queryOperations().offset(offset).limit(batch).complete().execute()
      else
        coreA.queryOperations().offset(offset).limit(batch).partial().execute()
      ).map { operations =>
        if (operations.size() <= 0) List[Operation]()
        else operations.asScala.toSeq.map { o => Operation.newInstance(o, coreC)}
      }
    }

    def sync(walletName: String, poolName: String)(implicit coreEC: core.ExecutionContext): Future[SynchronizationResult] = {
      val promise: Promise[SynchronizationResult] = Promise[SynchronizationResult]()
      val receiver: core.EventReceiver = new SynchronizationEventReceiver(coreA.getIndex, walletName, poolName, promise)
      coreA.synchronize().subscribe(coreEC, receiver)
      promise.future
    }

  }

  class Derivation(private val accountCreationInfo: core.AccountCreationInfo) {

    lazy val view: AccountDerivationView = AccountDerivationView(
      accountCreationInfo.getIndex,
      for {
        path <- accountCreationInfo.getDerivations.asScala.toList
        owner <- accountCreationInfo.getOwners.asScala.toList
      } yield DerivationView(path, owner, None, None))
  }


  def newInstance(coreA: core.Account, coreC: core.Currency)(implicit ec: ExecutionContext): Account = {
    new Account(coreA, coreC)
  }

  def newDerivation(coreD: core.AccountCreationInfo): Derivation = {
    new Derivation(coreD)
  }
}

case class AccountView(
                        @JsonProperty("wallet_name") walletName: String,
                        @JsonProperty("index") index: Int,
                        @JsonProperty("balance") balance: Long,
                        @JsonProperty("keychain") keychain: String,
                        @JsonProperty("currency") currency: CurrencyView
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