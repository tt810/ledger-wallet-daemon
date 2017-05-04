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

import java.net.InetSocketAddress

import org.java_websocket.WebSocket
import org.java_websocket.handshake.ClientHandshake
import org.java_websocket.server.WebSocketServer

class Server(address: InetSocketAddress) extends WebSocketServer(address) {

  private def init() = {

  }

  override def onError(webSocket: WebSocket, e: Exception): Unit = {

  }

  override def onMessage(webSocket: WebSocket, s: String): Unit = {
    println("Received", s)
    _connections.lift(webSocket) match {
      case Some(connection) =>
        connection.onMessage(s)
      case None =>
        System.err.println(s"Unable to find matching connection to ${webSocket.getResourceDescriptor}")
    }
  }

  override def onClose(webSocket: WebSocket, i: Int, s: String, b: Boolean): Unit = {
    _connections.remove(webSocket)
  }

  override def onOpen(webSocket: WebSocket, clientHandshake: ClientHandshake): Unit = {
    _connections(webSocket) = new Connection(webSocket)
  }

  override def onStart(): Unit = {

  }

  private val _connections = scala.collection.mutable.HashMap[WebSocket, Connection]()

  init()
}
