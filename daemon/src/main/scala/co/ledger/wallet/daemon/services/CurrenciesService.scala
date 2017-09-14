package co.ledger.wallet.daemon.services

import javax.inject.{Inject, Singleton}

import co.ledger.wallet.daemon.database.User
import co.ledger.core.implicits._
import co.ledger.wallet.daemon.models._

import scala.collection.JavaConverters._
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

@Singleton
class CurrenciesService @Inject()(poolsService: PoolsService) {

  def currency(user: User, poolName: String, currencyName: String): Future[Currency] = {
    val coreCurrency = for {
      pool <- poolsService.pool(user, poolName)
      coreCurrency <- pool.getCurrency(currencyName)
    } yield coreCurrency

    coreCurrency.map(newInstance(_))
  }

  def currencies(user: User, poolName: String): Future[Seq[Currency]] = {
    val currencies = for {
      pool <- poolsService.pool(user, poolName)
      coreCurrencies <- pool.getCurrencies()
    } yield coreCurrencies

    currencies.map(_.asScala.toList.map(newInstance(_)))
  }
}
