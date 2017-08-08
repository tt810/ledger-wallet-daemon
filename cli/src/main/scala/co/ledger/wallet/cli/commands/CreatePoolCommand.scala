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

package co.ledger.wallet.cli.commands

import co.ledger.wallet.cli.Client
import org.backuity.clist.Command

import scala.concurrent.Future
import org.backuity.clist._
import scala.concurrent.ExecutionContext.Implicits.global
object CreatePoolCommand extends Command(name = "pool/create", description = "Creates a new wallet pool") with CliCommand {

  var pool_name = arg[String](description = "The name of the newly created pool")
  var password = opt[Option[String]](description = "Optionally encrypts the pool with a password")
  var database = opt[Option[String]](description = "Sets the database backend sqlite|pgsql (sqlite by default)")
  var connect = opt[Option[String]](description = "Set the database connect string (uses the pool name by default)")
  var configuration = opt[Option[String]](description = "Path of the configuration file to use while opening the pool")

  override def run(client: Client): Future[Unit] = {
    val conf = configuration map { (filePath) =>
      val source = scala.io.Source.fromFile(filePath)
      val lines = try source.mkString finally source.close()
      lines
    }
    client.api.pool.createPool(pool_name, password.orNull, database.orNull, connect.orNull, conf.orNull) map protocolize
  }

}