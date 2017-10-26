package co.ledger.wallet.daemon.models

import co.ledger.core
import co.ledger.core.implicits.{CurrencyNotFoundException => CoreCurrencyNotFoundException, WalletNotFoundException => CoreWalletNotFoundException, _}
import co.ledger.wallet.daemon.clients.ClientFactory
import co.ledger.wallet.daemon.database.PoolDto
import co.ledger.wallet.daemon.exceptions.CurrencyNotFoundException
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
import scala.concurrent.{ExecutionContext, Future}

class Pool(private val coreP: core.WalletPool, val id: Long)(implicit ec: ExecutionContext) extends Logging {
  private val eventReceivers: mutable.Set[core.EventReceiver] = utils.newConcurrentSet[core.EventReceiver]

  val name: String = coreP.getName

  lazy val view: Future[WalletPoolView] = coreP.getWalletCount().map(WalletPoolView(coreP.getName, _))

  /**
    * Obtain wallets by batch size and offset. The performance of this call depends on core library.
    *
    * @param offset
    * @param batch
    * @return a tuple of total wallet count and a sequence of wallets from offset to batch size.
    */
  def wallets(offset: Int, batch: Int): Future[(Int, Seq[Wallet])] = {
    assert(offset >= 0, "offset must be non negative")
    assert(batch > 0, "batch must be positive")
    coreP.getWalletCount().flatMap { count =>
      coreP.getWallets(offset, batch).map { coreWs =>
        (count, coreWs.asScala.toSeq.map(Wallet.newInstance(_)))
      }
    }
  }

  /**
    * Obtain wallet by name. The performace of this call depends on core library.
    *
    * @param walletName
    * @return
    */
  def wallet(walletName: String): Future[Option[Wallet]] = {
    coreP.getWallet(walletName).map { coreW => Option(Wallet.newInstance(coreW)) }.recover {
      case e: CoreWalletNotFoundException => None
    }
  }

  /**
    * Obtain currency by name. The performance of this call depends on core library.
    *
    * @param currencyName
    * @return
    */
  def currency(currencyName: String): Future[Option[Currency]] =
    coreP.getCurrency(currencyName).map { coreC => Option(Currency.newInstance(coreC)) }.recover {
      case e: CoreCurrencyNotFoundException => None
    }

  /**
    * Obtain currencies. The performance of this call depends on core library.
    *
    * @return
    */
  def currencies(): Future[Seq[Currency]] =
    coreP.getCurrencies().map { coreCs =>
      coreCs.asScala.toSeq.map { core => Currency.newInstance(core)}
    }

  /**
    * Clear the event receivers on this pool. It will also call `stopRealTimeObserver` method.
    *
    * @return
    */
  def clear: Future[Unit] = {
    stopRealTimeObserver().map { _ =>
      unregisterEventReceivers()
    }
  }

  def addWalletIfNotExit(walletName: String, currencyName: String): Future[Wallet] = {
    coreP.getCurrency(currencyName).flatMap { coreC =>
      val coreW = coreP.createWallet(walletName, coreC, core.DynamicObject.newInstance()).map { wallet =>
        info(LogMsgMaker.newInstance("Wallet created").append("name", walletName).append("pool_name", name).append("currency_name", currencyName).toString())
        Future.successful(Wallet.newInstance(wallet))
      }.recover {
        case e: WalletAlreadyExistsException => {
          warn(LogMsgMaker.newInstance("Wallet already exist").append("name", walletName).append("pool_name", name).append("currency_name", currencyName).toString())
          coreP.getWallet(walletName).map(Wallet.newInstance)
        }
      }
      coreW.flatten.map { w =>
        if (DaemonConfiguration.realtimeObserverOn) w.startRealTimeObserver()
        w
      }
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
    wallets().flatMap { wallets =>
      Future.sequence(wallets.map { wallet => wallet.syncWallet(name)(coreEC) }).map(_.flatten)
    }
  }

  /**
    * Start real time observer of this pool will start the observers of the underlying wallets and accounts.
    *
    * @return
    */
  def startRealTimeObserver(): Future[Unit] = {
    wallets().map { ws => ws.foreach(_.startRealTimeObserver()) }
  }

  /**
    * Stop real time observer of this pool will stop the observers of the underlying wallets and accounts.
    *
    * @return
    */
  def stopRealTimeObserver(): Future[Unit] = {
    debug(LogMsgMaker.newInstance("Start real time observer").append("name", name).toString())
    wallets().map { ws => ws.foreach(_.stopRealTimeObserver()) }
  }

  override def equals(that: Any): Boolean = {
    that match {
      case that: Pool => that.isInstanceOf[Pool] && this.hashCode == that.hashCode
      case _ => false
    }
  }

  override def hashCode: Int = {
    this.name.hashCode
  }

  private def wallets(): Future[Seq[Wallet]] = {
    coreP.getWalletCount().flatMap { count =>
      coreP.getWallets(0, count).map { coreWs =>
        coreWs.asScala.toSeq.map(Wallet.newInstance)
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

