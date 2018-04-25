package co.ledger.wallet.daemon.clients

import javax.inject.Singleton

import co.ledger.wallet.daemon.configurations.DaemonConfiguration
import co.ledger.wallet.daemon.models.{CurrencyFamily, FeeMethod}
import co.ledger.wallet.daemon.utils._
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.{DeserializationFeature, ObjectMapper}
import com.fasterxml.jackson.module.scala.DefaultScalaModule
import com.fasterxml.jackson.module.scala.experimental.ScalaObjectMapper
import com.twitter.finagle.Http
import com.twitter.finagle.http.{Method, Request}

import scala.concurrent.{ExecutionContext, Future}

/**
  * Client for request to blockchain explorers.
  *
  * User: Ting Tu
  * Date: 24-04-2018
  * Time: 14:32
  *
  */
@Singleton
class ApiClient(implicit val ec: ExecutionContext) {
  import ApiClient._
  private[this] val (host, port, poolSize) = DaemonConfiguration.apiConnection
  private [this] val client = Http.client
    .withSessionPool.maxSize(poolSize)
    .newService(s"$host:$port")


  def getFees(currencyFamily: CurrencyFamily): Future[FeeInfo] = {
    val path = currencyFamily match {
      case CurrencyFamily.BITCOIN => "/blockchain/v2/btc/fees"
      case _ => throw new UnsupportedOperationException(s"currency not supported '$currencyFamily'")
    }
    val request = Request(Method.Get, path)
    request.host = "api.ledgerwallet.com"
    client(request).map { response =>
      mapper.readValue(response.contentString, classOf[FeeInfo])
    }.asScala
  }

  private val mapper: ObjectMapper = new ObjectMapper() with ScalaObjectMapper
  mapper.registerModule(DefaultScalaModule)
  mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
}

object ApiClient {
  case class FeeInfo(
                    @JsonProperty("1") fast: Long,
                    @JsonProperty("3") normal: Long,
                    @JsonProperty("6") slow: Long) {
    def getAmount(feeMethod: FeeMethod): Long = feeMethod match {
      case FeeMethod.FAST => fast
      case FeeMethod.NORMAL => normal
      case FeeMethod.SLOW => slow
      case _ => throw new UnsupportedOperationException(s"Fee method not supported '$feeMethod'")
    }
  }
}
