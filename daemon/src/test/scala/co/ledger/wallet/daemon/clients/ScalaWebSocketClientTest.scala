package co.ledger.wallet.daemon.clients

import java.util.concurrent.Executors

import co.ledger.core.{ErrorCode, WebSocketConnection}
import org.junit.Test
import org.scalatest.junit.AssertionsForJUnit


class ScalaWebSocketClientTest extends AssertionsForJUnit {

  @Test def verifyThreadedConnection(): Unit = {
    val client = ClientFactory.webSocketClient
    val nThreads = 10
    val executor = Executors.newFixedThreadPool(nThreads)
    val f = new Runnable {
      override def run(): Unit = client.connect("ws://echo.websocket.org", new TestWebSocketConnection)
    }
    (0 to nThreads).foreach(_ => executor.submit(f))

    Thread.sleep(20000L)
    val keys = ScalaWebSocketClient.sessions.keySet()
    assert(11 === keys.size())
    (1 to (nThreads + 1)).foreach(n => assert(keys.contains(n)))
  }



  class TestWebSocketConnection extends WebSocketConnection {
    override def onConnect(connectionId: Int): Unit = {
      conId = connectionId
      println(s"Connected with id $connectionId")
    }

    override def onClose(): Unit = println("Close connection")

    override def onMessage(data: String): Unit = println(s"Echo message: $data")

    override def onError(code: ErrorCode, message: String): Unit = println(s"Error: $code, Message: $message")

    override def getConnectionId: Int = conId

    private var conId: Int = -1
  }
}
