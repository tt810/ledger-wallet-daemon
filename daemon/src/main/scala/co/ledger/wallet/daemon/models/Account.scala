package co.ledger.wallet.daemon.models

import co.ledger.core
import co.ledger.core.implicits._
import co.ledger.wallet.daemon.DaemonConfiguration
import co.ledger.wallet.daemon.schedulers.observers.{SynchronizationEventReceiver, SynchronizationResult}
import co.ledger.wallet.daemon.services.LogMsgMaker
import co.ledger.wallet.daemon.utils.HexUtils
import com.fasterxml.jackson.annotation.JsonProperty

import scala.collection.JavaConverters._
import scala.concurrent.{Future, Promise}

object Account {

  class Account(private val coreA: core.Account, private val coreW: core.Wallet) extends Wallet(coreW) {
    private val self = this

    val accountIndex: Int = coreA.getIndex

    def balance: Future[Long] = coreA.getBalance().map { balance => balance.toLong }

    def accountView: Future[AccountView] = balance.map { b =>
      AccountView(walletName, accountIndex, b, "account key chain", currency.currencyView)
    }

    def operations(offset: Long, batch: Int, fullOp: Int): Future[Seq[Operation]] = {
      (if (fullOp > 0)
        coreA.queryOperations().offset(offset).limit(batch).complete().execute()
      else
        coreA.queryOperations().offset(offset).limit(batch).partial().execute()
      ).map { operations =>
        if (operations.size() <= 0) List[Operation]()
        else operations.asScala.toSeq.map { o => Operation.newInstance(o, coreA, coreW)}
      }
    }

    def syncAccount(poolName: String)(implicit coreEC: core.ExecutionContext): Future[SynchronizationResult] = {
      val promise: Promise[SynchronizationResult] = Promise[SynchronizationResult]()
      val receiver: core.EventReceiver = new SynchronizationEventReceiver(coreA.getIndex, walletName, poolName, promise)
      coreA.synchronize().subscribe(coreEC, receiver)
      promise.future
    }

    def startRealTimeObserver(): Unit = {
      if (DaemonConfiguration.realtimeObserverOn && !coreA.isObservingBlockchain) coreA.startBlockchainObservation()
      debug(LogMsgMaker.newInstance(s"Set real time observer on ${coreA.isObservingBlockchain}").append("account", self).toString())
    }

    override def stopRealTimeObserver(): Unit = {
      debug(LogMsgMaker.newInstance("Stop real time observer").append("account", self).toString())
      if (coreA.isObservingBlockchain) coreA.stopBlockchainObservation()
    }

    override def toString: String = s"Account(index: $accountIndex)"
  }

  class Derivation(private val accountCreationInfo: core.AccountCreationInfo) {
    val index = accountCreationInfo.getIndex

    lazy val view: AccountDerivationView = {
      val paths = accountCreationInfo.getDerivations.asScala.toSeq
      val owners = accountCreationInfo.getOwners.asScala.toSeq
      val pubKeys = {
        val pks = accountCreationInfo.getPublicKeys
        if (pks.isEmpty) paths.map { _ => null }
        else pks.asScala.toSeq.map(HexUtils.valueOf(_))
      }
      val chainCodes = {
        val ccs = accountCreationInfo.getChainCodes
        if (ccs.isEmpty) paths.map { _ => null }
        else ccs.asScala.toSeq.map(HexUtils.valueOf(_))
      }
      val derivations = (0 until paths.size).map { i =>
        DerivationView(paths(i), owners(i), Option(pubKeys(i)), Option(chainCodes(i)))
      }
      AccountDerivationView(index, derivations)
    }
  }


  def newInstance(coreA: core.Account, coreW: core.Wallet): Account = {
    new Account(coreA, coreW)
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
                         ) {
  override def toString: String = s"DerivationView(path: $path, owner: $owner, pub_key: $pubKey, chain_code: $chainCode)"
}

case class AccountDerivationView(
                                  @JsonProperty("account_index") accountIndex: Int,
                                  @JsonProperty("derivations") derivations: Seq[DerivationView]
                                ) {

  override def toString: String = s"AccountDerivationView(account_index: $accountIndex, derivations: $derivations)"
}