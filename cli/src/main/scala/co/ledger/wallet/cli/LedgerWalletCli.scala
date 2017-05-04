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

import co.ledger.wallet.cli.commands.{CliCommand, EchoCommand}
import org.backuity.clist._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.{Failure, Success}

object LedgerWalletCli extends App {
  var server: URI = _
  lazy val client: Client = new Client(server)

  private def exit(errorCode: Int) = {
    if (client.isOpen) client.close()
    sys.exit(errorCode)
  }

  Cli.parse(args)
    .withHelpCommand("help")
    .withProgramName("ledger-wallet-cli")
    .withCommand(new EchoCommand) {
      case command: CliCommand =>
        server = command.server
        client.ready flatMap {(_) =>
          command.run(client)
        } onComplete {
          case Success(_) =>
            exit(0)
          case Failure(ex) =>
            ex.printStackTrace()
            System.err.println(ex.getMessage)
            exit(1)
        }
        client.run()
    }
}
