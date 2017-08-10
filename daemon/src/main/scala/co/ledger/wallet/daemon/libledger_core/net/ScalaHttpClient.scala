package co.ledger.wallet.daemon.libledger_core.net
import java.io.{BufferedInputStream, DataOutputStream}
import java.net.{HttpURLConnection, URL}
import java.util

import co.ledger.core.{ErrorCode, HttpMethod, HttpReadBodyResult, HttpRequest}

import scala.collection.JavaConverters._
import scala.concurrent.Future
import scala.util.{Failure, Success, Try}

class ScalaHttpClient extends co.ledger.core.HttpClient {
  private implicit val ec = scala.concurrent.ExecutionContext.global

  override def execute(request: HttpRequest): Unit = Future {
    val connection = new URL(request.getUrl).openConnection().asInstanceOf[HttpURLConnection]
    connection.setRequestMethod(resolveMethod(request.getMethod))
    for ((key, value) <- request.getHeaders.asScala) {
       connection.setRequestProperty(key, value)
    }
    val body = request.getBody
    if (body.nonEmpty) {
      connection.setDoOutput(true)
      val dataOs = new DataOutputStream(connection.getOutputStream)
      dataOs.write(body)
      dataOs.flush()
      dataOs.close()
    }
    val statusCode = connection.getResponseCode
    val statusText = connection.getResponseMessage
    val response = new BufferedInputStream(connection.getInputStream)
    val headers = new util.HashMap[String, String]()
    for ((key, list) <- connection.getHeaderFields.asScala) {
      headers.put(key, list.get(list.size() - 1))
    }
    val proxy = new co.ledger.core.HttpUrlConnection() {
      private val buffer = new Array[Byte](4096)
      /**
        * Gets the HTTP response status code
        *
        * @return The HTTP response status code
        */
      override def getStatusCode: Int = statusCode

      /**
        * Gets the HTTP response status text
        *
        * @return The HTTP response status text
        */
      override def getStatusText: String = statusText

      /**
        * Gets the HTTP response headers
        *
        * @return The HTTP response headers
        */
      override def getHeaders: util.HashMap[String, String] = headers

      /**
        * Reads available HTTP response body. This method will be called multiple times until it returns a empty bytes array.
        *
        * @returns A chunk of the body data wrapped into a HttpReadBodyResult (for error management)
        */
      override def readBody(): HttpReadBodyResult = {
        Try {
          val size = response.read(buffer)
          if (size < buffer.length) {
            buffer.slice(0, size)
          } else {
            buffer
          }
        } match {
          case Success(data) =>
            new HttpReadBodyResult(null, data)
          case Failure(ex) =>
            val error = new co.ledger.core.Error(ErrorCode.HTTP_ERROR, "An error happened during body reading.")
            new HttpReadBodyResult(error, null)
        }
      }
    }
    request.complete(proxy, null)
  }.failed.map[co.ledger.core.Error]({
    case others: Throwable =>
      new co.ledger.core.Error(ErrorCode.HTTP_ERROR, others.getMessage)
  }).foreach(request.complete(null, _))

  private def resolveMethod(method: HttpMethod) = method match {
    case HttpMethod.GET => "GET"
    case HttpMethod.POST => "POST"
    case HttpMethod.PUT => "PUT"
    case HttpMethod.DEL => "DELETE"
  }
}
