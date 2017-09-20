package co.ledger.wallet.daemon.services

import javax.inject.{Inject, Singleton}

import co.ledger.wallet.daemon.database.User
import co.ledger.core.implicits._
import co.ledger.wallet.daemon.models.newInstance
import co.ledger.wallet.daemon.models.Currency

import scala.collection.JavaConverters._
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

@Singleton
class CurrenciesService @Inject()(poolsService: PoolsService) extends DaemonService {

  def currency(user: User, poolName: String, currencyName: String): Future[Currency] = {
    info(s"Obtain currency with params: poolName=$poolName currencyName=$currencyName userPubKey=${user.pubKey}")
    val coreCurrency = for {
      pool <- poolsService.pool(user, poolName)
      coreCurrency <- pool.getCurrency(currencyName)
    } yield coreCurrency

    coreCurrency.map { core =>
      val crcy = newInstance(core)
      info(s"Currency obtained: currency=$crcy")
      crcy
    }
  }

  def currencies(user: User, poolName: String): Future[Seq[Currency]] = {
    info(s"Obtain currencies with params: poolName=$poolName userPubKey=${user.pubKey}")
    val currencies = for {
      pool <- poolsService.pool(user, poolName)
      coreCurrencies <- pool.getCurrencies()
    } yield coreCurrencies

    currencies.map(_.asScala.toList.map(newInstance(_))).map { modelCs =>
      info(s"Currencies obtained: size=${modelCs.size} currencies=$modelCs")
      modelCs
    }
  }

  def addCurrency(user: User, poolName: String, currency: Currency): Future[Currency] = {
    poolsService.pool(user, poolName).flatMap { pool =>
      // TODO: implement me
      pool.getCurrency(currency.name).map { coreCurrency => currency }
    }
  }

  def removeCurrency(user: User, poolName: String, currencyName: String): Future[Unit] = {
    //TODO: implement me
    currency(user, poolName, currencyName).map(currency => ())
  }
}
