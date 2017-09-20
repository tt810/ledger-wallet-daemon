package co.ledger.wallet.daemon.filters

import javax.inject.Inject

import co.ledger.wallet.daemon.models.Currency
import com.twitter.finagle.{Service, SimpleFilter}
import com.twitter.finagle.http.{Request, Response}
import com.twitter.finatra.http.internal.marshalling.MessageBodyManager

class CurrencyDeserializeFilter @Inject()(messageBodyManager: MessageBodyManager) extends SimpleFilter[Request, Response] {

  override def apply(request: Request, service: Service[Request, Response]) = {
    val currency = messageBodyManager.read[Currency](request)
    CurrencyContext.setCurrency(request, currency)

    service(request)
  }
}

object CurrencyContext {
  private val CurrencyField = Request.Schema.newField[Currency]()
  implicit class CurrencyContextSyntax(val request: Request) extends AnyVal {
    def currency: Currency = request.ctx(CurrencyField)
  }
  def setCurrency(request: Request, currency: Currency): Unit = {
    request.ctx.update(CurrencyField, currency)
  }
}
