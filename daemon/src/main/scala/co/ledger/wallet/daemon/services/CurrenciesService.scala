package co.ledger.wallet.daemon.services

import javax.inject.{Inject, Singleton}

import co.ledger.wallet.daemon.database.{DefaultDaemonCache}
import co.ledger.wallet.daemon.models._

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

@Singleton
class CurrenciesService @Inject()(daemonCache: DefaultDaemonCache) extends DaemonService {

  def currency(currencyName: String, poolName: String): Future[Currency] = {
    info(s"Obtain currency with params: currencyName=$currencyName")
    daemonCache.getCurrency(poolName, currencyName).map(newInstance(_))
  }

  def currencies(poolName: String): Future[Seq[Currency]] = {
    info(s"Obtain currencies with params: poolName=$poolName")
    daemonCache.getCurrencies(poolName).map(_.map(newInstance(_))).map { modelCs =>
      info(s"Currencies obtained: size=${modelCs.size} currencies=$modelCs")
      modelCs
    }
  }
}
