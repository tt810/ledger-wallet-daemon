package co.ledger.wallet.daemon.models

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.{AtomicInteger, AtomicLong}

import co.ledger.core
import co.ledger.core.implicits
import co.ledger.core.implicits._
import co.ledger.wallet.daemon.async.MDCPropagatingExecutionContext
import co.ledger.wallet.daemon.exceptions.InvalidArgumentException
import co.ledger.wallet.daemon.models.Account.{Account, Derivation}
import co.ledger.wallet.daemon.schedulers.observers.SynchronizationResult
import co.ledger.wallet.daemon.services.LogMsgMaker
import co.ledger.wallet.daemon.utils.{AsArrayList, HexUtils}
import com.fasterxml.jackson.annotation.JsonProperty
import com.twitter.inject.Logging

import scala.collection.JavaConverters._
import scala.collection._
import scala.concurrent.{ExecutionContext, Future}
import scala.language.implicitConversions

class Wallet(private val coreW: core.Wallet) extends Logging {
  private val self = this

  implicit val ec: ExecutionContext = MDCPropagatingExecutionContext.Implicits.global
  implicit def asArrayList[T](input: Seq[T]): AsArrayList[T] = new AsArrayList[T](input)

  private val cachedAccounts: concurrent.Map[Int, Account] = new ConcurrentHashMap[Int, Account]().asScala
  private val accountLen: AtomicInteger = new AtomicInteger(0)
  private val configuration: Map[String, Any] = Map[String, Any]()
  private val currentBlockHeight: AtomicLong = new AtomicLong(-1)

  val walletName: String = coreW.getName
  val currency: Currency = Currency.newInstance(coreW.getCurrency)

  protected val initialBlockHeight: Future[Unit] = coreW.getLastBlock().map { lastBlock =>
    currentBlockHeight.updateAndGet(n => Math.max(lastBlock.getHeight, n))
  }

  def blockHeight: Long = currentBlockHeight.get()

  def updateBlockHeight(newHeight: Long): Unit = {
    currentBlockHeight.updateAndGet(n => Math.max(n, newHeight))
    debug(LogMsgMaker.newInstance("Update block height").append("to", blockHeight).append("wallet", walletName).toString())
  }

  def walletView: Future[WalletView] = {
    getBalance().map { b => WalletView(walletName, accountLen.get(), b, currency.currencyView, configuration) }
  }

  def account(index: Int): Future[Option[Account]] = {
    cachedAccounts.get(index) match {
      case Some(account) => Future.successful(Option(account))
      case None => toCacheAndStartListen(accountLen.get()).map { _ => cachedAccounts.get(index)}
    }
  }

  def accountCreationInfo(index: Option[Int]): Future[Derivation] = {
    (index match {
      case Some(i) => coreW.getAccountCreationInfo(i)
      case None => coreW.getNextAccountCreationInfo()
    }).map { info => Account.newDerivation(info) }
  }


  def accounts(): Future[Seq[Account]] = {
    coreW.getAccountCount().flatMap { count =>
      if(count == accountLen.get()) Future.successful(cachedAccounts.values.toSeq)
      else toCacheAndStartListen(accountLen.get()).map { _ => cachedAccounts.values.toSeq }
    }
  }

  def addAccountIfNotExit(accountDerivations: AccountDerivationView): Future[Account] = {
    val accountCreationInfo = new core.AccountCreationInfo(
      accountDerivations.accountIndex,
      (for (derivationResult <- accountDerivations.derivations) yield derivationResult.owner).asArrayList,
      (for (derivationResult <- accountDerivations.derivations) yield derivationResult.path).asArrayList,
      (for (derivationResult <- accountDerivations.derivations) yield HexUtils.valueOf(derivationResult.pubKey.get)).asArrayList,
      (for (derivationResult <- accountDerivations.derivations) yield HexUtils.valueOf(derivationResult.chainCode.get)).asArrayList
    )
    coreW.newAccountWithInfo(accountCreationInfo).map { coreA =>
      info(LogMsgMaker.newInstance("Account created").append("index", accountDerivations.accountIndex).append("wallet_name", walletName).toString())
      toCacheAndStartListen(accountLen.get()).map { _ => cachedAccounts(coreA.getIndex) }
    }.recover {
      case e: implicits.InvalidArgumentException => Future.failed(InvalidArgumentException(e.getMessage, e))
      case alreadyExist: implicits.AccountAlreadyExistsException =>
        warn(LogMsgMaker.newInstance("Account already exist").append("index", accountDerivations.accountIndex).append("wallet_name", walletName).toString())
        if(cachedAccounts.contains(accountDerivations.accountIndex)) Future.successful(cachedAccounts(accountDerivations.accountIndex))
        else toCacheAndStartListen(accountLen.get()).map { _ => cachedAccounts(accountDerivations.accountIndex) }
    }.flatten
  }

  def syncWallet(poolName: String)(implicit coreEC: core.ExecutionContext): Future[Seq[SynchronizationResult]] = {
    accounts().flatMap { accounts =>
      Future.sequence(accounts.map { account => account.syncAccount(poolName)})
    }
  }

  def startCacheAndRealTimeObserver(): Future[Unit] = {
    toCacheAndStartListen(accountLen.get())
  }

  def stopRealTimeObserver(): Unit = {
    debug(LogMsgMaker.newInstance("Stop real time observer").append("wallet", self).toString())
    cachedAccounts.values.toSeq.map { account => account.stopRealTimeObserver() }
  }

  override def equals(that: Any): Boolean = {
    that match {
      case that: Wallet => that.isInstanceOf[Wallet] && self.hashCode == that.hashCode
      case _ => false
    }
  }

  override def hashCode: Int = {
    self.walletName.hashCode + self.currency.hashCode()
  }

  override def toString: String = s"Wallet(name: $walletName, currency: ${currency.currencyName})"

  private def toCacheAndStartListen(offset: Int): Future[Unit] = {
    if (offset < accountLen.get()) Future { info(s"Wallet $walletName cache was already updated")}
    else
      coreW.getAccountCount().flatMap { count =>
        if (count == offset) Future { info(s"Wallet $walletName cache is up to date") }
        else if (count < offset) Future { warn(s"Offset should be less than count, possible race condition")}
        else {
          coreW.getAccounts(offset, count).map { coreAs =>
            coreAs.asScala.toSeq.map { coreA =>
              cachedAccounts.put(coreA.getIndex, Account.newInstance(coreA, coreW))
              debug(s"Add to Wallet(name: $walletName) cache, Account(index: ${coreA.getIndex})")
              cachedAccounts(coreA.getIndex).startRealTimeObserver()
            }.map { _ =>
              accountLen.updateAndGet(n => Math.max(n, count))
            }
          }
        }
      }
  }

  private def getBalance(): Future[Long] = {
    accounts().flatMap { as =>
      Future.sequence( for (a <- as) yield a.balance ).map { b => b.sum }
    }
  }

}

object Wallet {
  def newInstance(coreW: core.Wallet): Wallet = {
    new Wallet(coreW)
  }
}

case class WalletView(
                       @JsonProperty("name") name: String,
                       @JsonProperty("account_count") accountCount: Int,
                       @JsonProperty("balance") balance: Long,
                       @JsonProperty("currency") currency: CurrencyView,
                       @JsonProperty("configuration") configuration: Map[String, Any]
                     )

case class WalletsViewWithCount(count: Int, wallets: Seq[WalletView])