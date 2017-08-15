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

import djinni.NativeLibLoader
import com.typesafe.config.ConfigFactory
import org.backuity.clist._

import scala.util.Try

object LedgerWalletDaemon extends CliMain[Unit] {
  var port = opt[Int](description = "Server listening port", default = 4060)
  lazy val server = new Server(new InetSocketAddress("localhost", port))
  lazy val manager = new PoolsManagerService()
  lazy val configuration = ConfigFactory.load()

  val profileName = Try(LedgerWalletDaemon.configuration.getString("database_engine")).toOption.getOrElse("sqlite3")
  val profile = {
    profileName match {
      case "sqlite3" =>
        slick.jdbc.SQLiteProfile
      case "postgres" =>
        slick.jdbc.PostgresProfile
      case others => throw new Exception(s"Unknown database backend $others")
    }
  }

  override def run: Unit = {
    NativeLibLoader.loadLibs()
    manager.start()
    server.run()
  }
}
