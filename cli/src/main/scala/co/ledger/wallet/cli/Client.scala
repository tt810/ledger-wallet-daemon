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

package co.ledger.wallet.cli

import java.net.URI

import io.github.shogowada.scala.jsonrpc.JSONRPCServerAndClient
import io.github.shogowada.scala.jsonrpc.client.JSONRPCClient
import io.github.shogowada.scala.jsonrpc.serializers.UpickleJSONSerializer
import io.github.shogowada.scala.jsonrpc.server.JSONRPCServer
import org.java_websocket.client.WebSocketClient
import org.java_websocket.drafts.{Draft, Draft_17}
import org.java_websocket.handshake.ServerHandshake
import co.ledger.wallet.protocol.{EchoApi, LedgerCoreApi, PoolApi}

import scala.concurrent.{Future, Promise}

class Client(uri: URI, draft: Draft = new Draft_17) extends WebSocketClient(uri, draft) {
  private implicit val ec = scala.concurrent.ExecutionContext.Implicits.global

  lazy val rpc = {
    val serializer = UpickleJSONSerializer()
    val client = JSONRPCClient(serializer, sendJson)
    val server = JSONRPCServer(serializer)
    JSONRPCServerAndClient(server, client)
  }

  override def onError(e: Exception): Unit = {
    if (!_connectionPromise.isCompleted) {
      _connectionPromise.failure(new Exception(s"Connection closed [${e.getCause}]"))
    }
  }

  override def onMessage(s: String): Unit = {
    rpc.receiveAndSend(s)
  }

  override def onClose(i: Int, s: String, b: Boolean): Unit = {
    if (!_connectionPromise.isCompleted) {
      _connectionPromise.failure(new Exception(s"Connection closed [${s}]"))
    }
  }

  override def onOpen(serverHandshake: ServerHandshake): Unit = {
    _connectionPromise.success()
  }

  def ready: Future[Unit] = _connectionPromise.future

  private val sendJson: (String) => Future[Option[String]] = {(json: String) =>
    send(json)
    Future.successful(None)
  }

  object api {
    val echo = rpc.client.createAPI[EchoApi]
    val pool = rpc.client.createAPI[PoolApi]
    val lib = rpc.client.createAPI[LedgerCoreApi]
  }

  private val _connectionPromise = Promise[Unit]()
}
