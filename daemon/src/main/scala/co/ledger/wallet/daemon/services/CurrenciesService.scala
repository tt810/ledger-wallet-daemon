package co.ledger.wallet.daemon.services

import javax.inject.{Inject, Singleton}

import co.ledger.wallet.daemon.database.{DaemonCache, DefaultDaemonCache}
import co.ledger.wallet.daemon.models._

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class CurrenciesService @Inject()(daemonCache: DaemonCache) extends DaemonService {

  def currency(currencyName: String, poolName: String)(implicit ec: ExecutionContext): Future[CurrencyView] = {
    info(LogMsgMaker.newInstance("Obtain currency with params")
      .append("currency_name", currencyName)
      .append("pool_name", poolName)
      .toString())
    daemonCache.getCurrency(currencyName, poolName)
  }

  def currencies(poolName: String)(implicit ec: ExecutionContext): Future[Seq[CurrencyView]] = {
    info(LogMsgMaker.newInstance("Obtain currencies with params")
      .append("pool_name", poolName)
      .toString())
    daemonCache.getCurrencies(poolName).map { modelCs =>
      info(s"Currencies obtained: size=${modelCs.size} currencies=$modelCs")
      modelCs
    }
  }
}
