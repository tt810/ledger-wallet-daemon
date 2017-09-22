package co.ledger.wallet.daemon.services

import javax.inject.{Inject, Singleton}

import co.ledger.wallet.daemon.database.{DefaultDaemonCache, User}
import co.ledger.core.implicits._
import co.ledger.wallet.daemon.models._

import scala.collection.JavaConverters._
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

@Singleton
class CurrenciesService @Inject()(daemonCache: DefaultDaemonCache) extends DaemonService {

  def currency(user: User, poolName: String, currencyName: String): Future[Currency] = {
    info(s"Obtain currency with params: poolName=$poolName currencyName=$currencyName userPubKey=${user.pubKey}")
    daemonCache.getPool(user.id.get, poolName).flatMap(_.getCurrency(currencyName).map(newInstance(_)))
  }

  def currencies(user: User, poolName: String): Future[Seq[Currency]] = {
    info(s"Obtain currencies with params: poolName=$poolName userPubKey=${user.pubKey}")
    daemonCache.getPool(user.id.get, poolName).flatMap(
      _.getCurrencies().map(
        _.asScala.toList.map(newInstance(_))).map { modelCs =>
          info(s"Currencies obtained: size=${modelCs.size} currencies=$modelCs")
          modelCs
    })
  }
}
