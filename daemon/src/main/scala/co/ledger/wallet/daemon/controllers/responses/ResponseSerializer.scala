package co.ledger.wallet.daemon.controllers.responses

import com.twitter.finagle.http.Response
import com.twitter.finatra.http.response.ResponseBuilder
import com.twitter.inject.Logging

class ResponseSerializer extends Logging {

  def serializeInternalErrorToOk(response: ResponseBuilder, caught: Throwable): Response = {
    error(ErrorCode.Internal_Error, caught)
    response.ok()
      .body(ErrorResponseBody(ErrorCode.Internal_Error, Map("response"->"Problem occurred when processing the request, check with developers")))
  }

  def serializeBadRequest(msg: Map[String, Any], response: ResponseBuilder, caught: Exception): Response = {
    info(ErrorCode.Bad_Request, caught)
    response.badRequest()
      .body(ErrorResponseBody(ErrorCode.Bad_Request, msg))
  }

  def serializeNotFound(msg: Map[String, Any], response: ResponseBuilder, caught: Exception): Response = {
    info(ErrorCode.Not_Found, caught)
    response.notFound()
      .body(ErrorResponseBody(ErrorCode.Not_Found, msg))
  }
}

object ResponseSerializer {
  def newInstance(): ResponseSerializer = {
    new ResponseSerializer()
  }
}
