package co.ledger.wallet.daemon.mappers

import javax.inject.{Inject, Singleton}

import co.ledger.wallet.daemon.exceptions.OtherCoreException
import com.twitter.finagle.http.{Request, Response}
import com.twitter.finatra.http.exceptions.ExceptionMapper
import com.twitter.finatra.http.response.ResponseBuilder

@Singleton
class OtherCoreExceptionMapper @Inject()(response: ResponseBuilder)
  extends ExceptionMapper[OtherCoreException] {

  override def toResponse(request: Request, throwable: OtherCoreException): Response = {
    response.internalServerError(throwable.getMessage)
  }

}
