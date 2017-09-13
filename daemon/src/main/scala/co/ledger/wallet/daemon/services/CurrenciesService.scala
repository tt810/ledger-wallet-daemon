package co.ledger.wallet.daemon.services

import javax.inject.{Inject, Singleton}

import co.ledger.wallet.daemon.database.User
import co.ledger.core.implicits._
import co.ledger.wallet.daemon.exceptions.{OtherCoreException, ResourceNotFoundException}
import co.ledger.wallet.daemon.models._
import co.ledger.wallet.daemon.services.PoolsService.PoolNotFoundException
import com.twitter.finatra.http.exceptions.BadRequestException

import scala.collection.JavaConverters._
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

@Singleton
class CurrenciesService @Inject()(poolsService: PoolsService) {
  import CurrenciesService._

  def currency(user: User, poolName: String, currencyName: String): Future[Currency] = {
    val coreCurrency = for {
      pool <- poolsService.pool(user, poolName)
      coreCurrency <- pool.getCurrency(currencyName)
    } yield coreCurrency

    coreCurrency recover {
      case pnfe: PoolNotFoundException => throw new BadRequestException(pnfe.getMessage)
      case cnfe: CurrencyNotFoundException => throw new ResourceNotFoundException(cnfe.getMessage)
      case e: Throwable => throw new OtherCoreException(s"Other Exception $e.getMessage")
    } map(newInstance(_))

  }

  def currencies(user: User, poolName: String): Future[List[Currency]] = {
    val currencies = for {
      pool <- poolsService.pool(user, poolName)
      coreCurrencies <- pool.getCurrencies()
    } yield coreCurrencies

    currencies recover {
      case pnfe: PoolNotFoundException => throw new BadRequestException(pnfe.getMessage)
      case e: Throwable => throw new OtherCoreException(s"Other Exception $e.getMessage")
    } map(_.asScala.toList.map(newInstance(_)))
  }
}

object CurrenciesService {

}
