package co.ledger.wallet.daemon.models

import java.util.concurrent.ConcurrentHashMap

import co.ledger.core
import co.ledger.core.implicits.{CurrencyNotFoundException => CoreCurrencyNotFoundException, WalletNotFoundException => CoreWalletNotFoundException, _}
import co.ledger.wallet.daemon.DaemonConfiguration
import co.ledger.wallet.daemon.clients.ClientFactory
import co.ledger.wallet.daemon.database.PoolDto
import co.ledger.wallet.daemon.exceptions.CurrencyNotFoundException
import co.ledger.wallet.daemon.libledger_core.crypto.SecureRandomRNG
import co.ledger.wallet.daemon.libledger_core.debug.NoOpLogPrinter
import co.ledger.wallet.daemon.libledger_core.filesystem.ScalaPathResolver
import co.ledger.wallet.daemon.schedulers.observers.SynchronizationResult
import co.ledger.wallet.daemon.services.LogMsgMaker
import co.ledger.wallet.daemon.utils.HexUtils
import com.fasterxml.jackson.annotation.JsonProperty
import com.twitter.inject.Logging
import org.bitcoinj.core.Sha256Hash

import scala.collection.JavaConverters._
import scala.collection._
import scala.concurrent.{ExecutionContext, Future}

class Pool(private val coreP: core.WalletPool)(implicit ec: ExecutionContext) extends Logging {
  private var eventReceivers: concurrent.Map[core.EventReceiver, Unit] = new ConcurrentHashMap[core.EventReceiver, Unit]().asScala

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

  def addWalletIfNotExit(walletName: String, currencyName: String): Future[Wallet] = {
    coreP.getCurrency(currencyName).flatMap { coreC =>
      val coreW = coreP.createWallet(walletName, coreC, core.DynamicObject.newInstance()).map { wallet =>
        info(LogMsgMaker.newInstance("Wallet created").append("name", walletName).append("pool_name", name).append("currency_name", currencyName).toString())
        Future.successful(Wallet.newInstance(wallet))
      }.recover {
        case e: WalletAlreadyExistsException => {
          warn(LogMsgMaker.newInstance("Wallet already exist").append("name", walletName).append("pool_name", name).append("currency_name", currencyName).toString())
          coreP.getWallet(walletName).map(Wallet.newInstance(_))
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

  def registerEventReceiver(eventReceiver: core.EventReceiver, coreEC: core.ExecutionContext) = {
    eventReceivers.put(eventReceiver, Unit)
    coreP.getEventBus.subscribe(coreEC, eventReceiver)
    debug(LogMsgMaker.newInstance(s"Register ${eventReceiver.getClass.getSimpleName}").append("pool_name", name).toString())
  }

  def unregisterEventReceivers() = {
    eventReceivers.keys.foreach { eventReceiver =>
      coreP.getEventBus.unsubscribe(eventReceiver)
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

  def startRealTimeObserver(): Unit = {
    wallets().map { ws => ws.map(_.startRealTimeObserver()) }
  }

  def stopRealTimeObserver() = {
    wallets().map { ws => ws.map(_.stopRealTimeObserver()) }
  }

  override def equals(that: Any): Boolean = {
    that match {
      case that: Pool => that.isInstanceOf[Pool] && this.hashCode == that.hashCode
      case _ => false
    }
  }

  override def hashCode: Int = {
    return this.name.hashCode
  }

  private def wallets(): Future[Seq[Wallet]] = {
    coreP.getWalletCount().flatMap { count =>
      coreP.getWallets(0, count).map { coreWs =>
        coreWs.asScala.toSeq.map(Wallet.newInstance(_))
      }
    }
  }
}

object Pool {
  def newInstance(coreP: core.WalletPool)(implicit ec: ExecutionContext): Pool = {
    new Pool(coreP)
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

  private def corePoolId(userId: Long, poolName: String): String = HexUtils.valueOf(Sha256Hash.hash(s"${userId}:${poolName}".getBytes))
}

case class WalletPoolView(
                           @JsonProperty("name") name: String,
                           @JsonProperty("wallet_count") walletCount: Int
                         )

