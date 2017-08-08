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

package co.ledger.wallet.daemon.api

import co.ledger.wallet.daemon.{LedgerWalletDaemon, database}
import co.ledger.wallet.protocol.{PoolApi, PoolDescription, RPCError, RPCFuture}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class PoolApiImpl extends PoolApi {
  import LedgerWalletDaemon.profile.api._

  override def listPools(): Future[Either[RPCError, Array[PoolDescription]]] = LedgerWalletDaemon.manager.database flatMap {(db) =>
    db.run(database.pools.result).map {(pools) =>
      pools.toArray.map {
        case (name, _, _, dbBackend, dbConnectString) =>
          PoolDescription(name, LedgerWalletDaemon.manager.isPoolOpen(name), dbBackend)
      }
    }
  } transform protocolize

  override def createPool(name: String, password: String, databaseBackendName: String, dbConnectString: String,
                          configuration: String): Future[Either[RPCError, Unit]] = {
    LedgerWalletDaemon.manager.createPool(name, Option(password), Option(databaseBackendName), Option(dbConnectString),
      Option(configuration)) transform protocolize
  }

  override def openPool(name: String, password: String): Future[Either[RPCError, Unit]] = ???
}
