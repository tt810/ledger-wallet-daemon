package co.ledger.wallet.daemon.models

import java.util.concurrent.ConcurrentHashMap

import co.ledger.core
import co.ledger.core.implicits.{CurrencyNotFoundException => CoreCurrencyNotFoundException, WalletNotFoundException => CoreWalletNotFoundException, _}
import co.ledger.wallet.daemon.clients.ClientFactory
import co.ledger.wallet.daemon.database.PoolDto
import co.ledger.wallet.daemon.exceptions.{CurrencyNotFoundException, WalletNotFoundException}
import co.ledger.wallet.daemon.libledger_core.crypto.SecureRandomRNG
import co.ledger.wallet.daemon.libledger_core.debug.NoOpLogPrinter
import co.ledger.wallet.daemon.libledger_core.filesystem.ScalaPathResolver
import co.ledger.wallet.daemon.schedulers.observers.SynchronizationResult
import co.ledger.wallet.daemon.services.LogMsgMaker
import co.ledger.wallet.daemon.utils.HexUtils
import co.ledger.wallet.daemon.{DaemonConfiguration, utils}
import com.fasterxml.jackson.annotation.JsonProperty
import com.twitter.inject.Logging
import org.bitcoinj.core.Sha256Hash

import scala.collection.JavaConverters._
import scala.collection._
import scala.collection.mutable.ListBuffer
import scala.concurrent.{ExecutionContext, Future}

class Pool(private val coreP: core.WalletPool, val id: Long)(implicit ec: ExecutionContext) extends Logging {
  private val eventReceivers: mutable.Set[core.EventReceiver] = utils.newConcurrentSet[core.EventReceiver]
  private val cachedWallets: concurrent.Map[String, Wallet] = new ConcurrentHashMap[String, Wallet]().asScala
  private val cachedCurrencies: concurrent.Map[String, Currency] = new ConcurrentHashMap[String, Currency]().asScala
  private var orderedNames = new ListBuffer[String]()

  val name: String = coreP.getName

  lazy val view: Future[WalletPoolView] = toCacheAndStartListen(orderedNames.length).map { _ =>
    WalletPoolView(name, orderedNames.length)
  }

  /**
    * Obtain wallets by batch size and offset. If the specified offset and batch size are within the existing
    * cached wallets, the cached results will be returned. Otherwise the difference will be retrieved from core.
    *
    * @param offset
    * @param batch
    * @return a tuple of total wallet count and a sequence of wallets from offset to batch size.
    */
  def wallets(offset: Int, batch: Int): Future[(Int, Seq[Wallet])] = {
    assert(offset >= 0 && offset <= orderedNames.length, s"offset invalid $offset")
    assert(batch > 0, "batch must be positive")
    if (offset + batch <= orderedNames.length) {
      Future.successful(fromCache(offset, batch)).map { wallets => (orderedNames.length, wallets) }
    } else {
       toCacheAndStartListen(orderedNames.length).map { _ =>
         val endCount = orderedNames.length min (offset + batch)
         (endCount, fromCache(offset, endCount))
       }
    }
  }

  private def fromCache(offset: Int, batch: Int): Seq[Wallet] = {
    (offset until (offset + batch)).map { index =>
      cachedWallets.get(orderedNames(index)) match {
        case Some(wallet) => wallet
        case None => throw WalletNotFoundException(orderedNames(index))
      }
    }
  }

  /**
    * Obtain wallet by name. If the name doesn't exist in local cache, a core retrieval will be performed.
    *
    * @param walletName
    * @return
    */
  def wallet(walletName: String): Future[Option[Wallet]] = {
    cachedWallets.get(walletName) match {
      case Some(wallet) => Future.successful(Option(wallet))
      case None => toCacheAndStartListen(orderedNames.length).map { _ => cachedWallets.get(walletName)}
    }
  }

  /**
    * Obtain currency by name. If can not find a result from cache, a call to core will be performed.
    *
    * @param currencyName
    * @return
    */
  def currency(currencyName: String): Future[Option[Currency]] = cachedCurrencies.get(currencyName) match {
    case Some(c) => Future.successful(Option(c))
    case None => coreP.getCurrency(currencyName).map { coreC =>
      toCache(coreC)
      cachedCurrencies.get(currencyName)
    }.recover {
      case e: CoreCurrencyNotFoundException => None
    }
  }

  /**
    * Obtain currencies.
    *
    * @return
    */
  def currencies(): Future[Seq[Currency]] =
    if (cachedCurrencies.isEmpty) currenciesFromCore.map { _ => cachedCurrencies.values.toSeq }
    else Future.successful(cachedCurrencies.values.toSeq)


  private val currenciesFromCore: Future[Unit] = coreP.getCurrencies().map { coreCs =>
    coreCs.asScala.toSeq.map { coreC =>
      toCache(coreC)
    }
  }

  private def toCache(coreC: core.Currency): Unit = {
    debug(s"Add to Pool(name: $name) cache, Currency(name: ${coreC.getName})")
    cachedCurrencies.put(coreC.getName, Currency.newInstance(coreC))
  }

  /**
    * Clear the event receivers on this pool. It will also call `stopRealTimeObserver` method.
    *
    * @return
    */
  def clear: Future[Unit] = {
    Future.successful(stopRealTimeObserver).map { _ =>
      unregisterEventReceivers
    }
  }

  def addWalletIfNotExit(walletName: String, currencyName: String): Future[Wallet] = {
    coreP.getCurrency(currencyName).flatMap { coreC =>
      coreP.createWallet(walletName, coreC, core.DynamicObject.newInstance()).map { wallet =>
        info(LogMsgMaker.newInstance("Wallet created").append("name", walletName).append("pool_name", name).append("currency_name", currencyName).toString())
        toCacheAndStartListen(orderedNames.length).map { _ => cachedWallets(walletName) }
      }.recover {
        case e: WalletAlreadyExistsException => {
          warn(LogMsgMaker.newInstance("Wallet already exist").append("name", walletName).append("pool_name", name).append("currency_name", currencyName).toString())
          if (cachedWallets.contains(walletName)) Future.successful(cachedWallets(walletName))
          else toCacheAndStartListen(orderedNames.length).map { _ => cachedWallets(walletName) }
        }
      }.flatten
    }.recover {
      case e: CoreCurrencyNotFoundException => throw new CurrencyNotFoundException(currencyName)
    }
  }

  /**
    * Subscribe specied event receiver to core pool, also save the event receiver to the local container.
    *
    * @param eventReceiver
    * @param coreEC
    */
  def registerEventReceiver(eventReceiver: core.EventReceiver, coreEC: core.ExecutionContext): Unit = {
    if (! eventReceivers.contains(eventReceiver)) {
      eventReceivers += eventReceiver
      coreP.getEventBus.subscribe(coreEC, eventReceiver)
      debug(LogMsgMaker.newInstance(s"Register ${eventReceiver.getClass.getSimpleName}").append("pool_name", name).toString())
    } else
      debug(LogMsgMaker.newInstance(s"${eventReceiver.getClass.getSimpleName} already registered").append("pool_name", name).toString())
  }

  /**
    * Unsubscribe all event receivers for this pool, including empty the event receivers container in memory.
    *
    */
  def unregisterEventReceivers(): Unit = {
    eventReceivers.foreach { eventReceiver =>
      coreP.getEventBus.unsubscribe(eventReceiver)
      eventReceivers.remove(eventReceiver)
      debug(LogMsgMaker.newInstance(s"Unregister ${eventReceiver.getClass.getSimpleName}").append("pool_name", name).toString())
    }
  }

  /**
    * Synchronize all accounts within this pool.
    *
    * @param coreEC
    * @return
    */
  def sync()(coreEC: core.ExecutionContext): Future[Seq[SynchronizationResult]] = {
    toCacheAndStartListen(orderedNames.length).flatMap { _ =>
      Future.sequence(cachedWallets.values.map { wallet => wallet.syncWallet(name)(coreEC) }.toSeq).map(_.flatten)
    }
  }

  /**
    * Start real time observer of this pool will start the observers of the underlying wallets and accounts.
    *
    * @return
    */
  def startCacheAndRealTimeObserver(): Future[Unit] = {
    toCacheAndStartListen(orderedNames.length).map { _ =>
      cachedWallets.values.foreach { wallet =>
        if (DaemonConfiguration.realtimeObserverOn) wallet.startRealTimeObserver()
      }
    }
  }

  /**
    * Stop real time observer of this pool will stop the observers of the underlying wallets and accounts.
    *
    * @return
    */
  def stopRealTimeObserver(): Unit = {
    debug(LogMsgMaker.newInstance("Start real time observer").append("name", name).toString())
    cachedWallets.values.foreach { wallet => wallet.stopRealTimeObserver() }
  }

  override def equals(that: Any): Boolean = {
    that match {
      case that: Pool => that.isInstanceOf[Pool] && this.hashCode == that.hashCode
      case _ => false
    }
  }

  override def hashCode: Int = {
    this.id.hashCode() + this.name.hashCode
  }

  private def toCacheAndStartListen(offset: Int): Future[Unit] = {
    coreP.getWalletCount().flatMap { count =>
      if (count == offset) Future.successful()
      else {
        assert(offset < count, "Offset must be less than total wallet count")
        coreP.getWallets(offset, count).map { coreWs =>
          coreWs.asScala.toSeq.map(Wallet.newInstance).map { wallet =>
            orderedNames += wallet.walletName
            cachedWallets.put(wallet.walletName, wallet)
            debug(s"Add to Pool(name: $name) cache, Wallet(name: ${wallet.walletName}, currency: ${wallet.currency.currencyName})")
            wallet.startRealTimeObserver()
          }
        }
      }
    }
  }

}

object Pool {
  def newInstance(coreP: core.WalletPool, id: Long)(implicit ec: ExecutionContext): Pool = {
    new Pool(coreP, id)
  }

  def newCoreInstance(poolDto: PoolDto): Future[core.WalletPool] = {
    core.WalletPoolBuilder.createInstance()
      .setHttpClient(ClientFactory.httpClient)
      .setWebsocketClient(ClientFactory.webSocketClient)
      .setLogPrinter(new NoOpLogPrinter(ClientFactory.threadDispatcher.getMainExecutionContext))
      .setThreadDispatcher(ClientFactory.threadDispatcher)
      .setPathResolver(new ScalaPathResolver(corePoolId(poolDto.userId, poolDto.name)))
      .setRandomNumberGenerator(new SecureRandomRNG)
      .setDatabaseBackend(core.DatabaseBackend.getSqlite3Backend)
      .setConfiguration(core.DynamicObject.newInstance())
      .setName(poolDto.name)
      .build()
  }

  private def corePoolId(userId: Long, poolName: String): String = HexUtils.valueOf(Sha256Hash.hash(s"$userId:$poolName".getBytes))
}

case class WalletPoolView(
                           @JsonProperty("name") name: String,
                           @JsonProperty("wallet_count") walletCount: Int
                         )

