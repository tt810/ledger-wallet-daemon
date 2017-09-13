package co.ledger.wallet.daemon.mappers

import javax.inject.{Inject, Singleton}

import co.ledger.wallet.daemon.exceptions.ResourceNotFoundException
import com.twitter.finagle.http.{Request, Response}
import com.twitter.finatra.http.exceptions.ExceptionMapper
import com.twitter.finatra.http.response.ResponseBuilder

@Singleton
class ResourceNotFoundExceptionMapper @Inject() (response: ResponseBuilder)
  extends ExceptionMapper[ResourceNotFoundException] {

  override def toResponse(request: Request, throwable: ResourceNotFoundException): Response = {
    response.notFound(throwable.getMessage)
  }
}
