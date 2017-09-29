package co.ledger.wallet.daemon.services

import javax.inject.{Inject, Singleton}

import co.ledger.wallet.daemon.database.{DefaultDaemonCache}
import co.ledger.wallet.daemon.models._

import scala.concurrent.Future
import co.ledger.wallet.daemon.async.MDCPropagatingExecutionContext.Implicits.global

@Singleton
class CurrenciesService @Inject()(daemonCache: DefaultDaemonCache) extends DaemonService {

  def currency(currencyName: String, poolName: String): Future[Currency] = {
    info(LogMsgMaker.newInstance("Obtain currency with params")
      .append("currencyName", currencyName)
      .append("poolName", poolName)
      .toString())
    daemonCache.getCurrency(poolName, currencyName).map(newInstance(_))
  }

  def currencies(poolName: String): Future[Seq[Currency]] = {
    info(LogMsgMaker.newInstance("Obtain currencies with params")
      .append("poolName", poolName)
      .toString())
    daemonCache.getCurrencies(poolName).map(_.map(newInstance(_))).map { modelCs =>
      info(s"Currencies obtained: size=${modelCs.size} currencies=$modelCs")
      modelCs
    }
  }
}
