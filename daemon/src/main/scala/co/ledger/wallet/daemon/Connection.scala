/*
 * MIT License
 *
 * Copyright (c) 2017 Ledger
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package co.ledger.wallet.daemon

import co.ledger.wallet.daemon.api.{EchoApiImpl, PoolApiImpl}
import co.ledger.wallet.protocol.{EchoApi, PoolApi}
import io.github.shogowada.scala.jsonrpc.JSONRPCServerAndClient
import io.github.shogowada.scala.jsonrpc.client.JSONRPCClient
import io.github.shogowada.scala.jsonrpc.serializers.UpickleJSONSerializer
import io.github.shogowada.scala.jsonrpc.server.JSONRPCServer
import org.java_websocket.WebSocket

import scala.concurrent.Future
import scala.util.{Failure, Success}

class Connection(val websocket: WebSocket) {
  private implicit val ec = scala.concurrent.ExecutionContext.Implicits.global

  lazy val rpc = {
    val serializer = UpickleJSONSerializer()
    val client = JSONRPCClient(serializer, sendJson)
    val server = JSONRPCServer(serializer)
    JSONRPCServerAndClient(server, client)
  }

  def onMessage(json: String) = {
    rpc.server.receive(json) onComplete {
      case Success(Some(response)) => sendJson(response)
      case Success(None) => // Do nothing
      case Failure(ex) =>
        System.err.println("RPC exception")
        ex.printStackTrace()
    }
  }

  private val sendJson: (String) => Future[Option[String]] = {(json: String) =>
    println(json)
    websocket.send(json)
    Future.successful(None)
  }

  private def init(): Unit = {
    rpc.bindAPI[EchoApi](new EchoApiImpl)
    rpc.bindAPI[PoolApi](new PoolApiImpl)
  }

  init()
}
