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

import org.java_websocket.client.WebSocketClient
import org.java_websocket.drafts.{Draft, Draft_17}
import org.java_websocket.handshake.ServerHandshake

class Client(uri: URI, draft: Draft = new Draft_17) extends WebSocketClient(uri, draft) {

  override def onError(e: Exception): Unit = {
    println(s"Error ${e.getMessage}")
    e.printStackTrace()
  }

  override def onMessage(s: String): Unit = {
    println(s"Received message: '$s'")
  }

  override def onClose(i: Int, s: String, b: Boolean): Unit = {
    println("Connection closed.")
  }

  override def onOpen(serverHandshake: ServerHandshake): Unit = {
    println("Opening connection...")
    send("Hello you")
  }


}
