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
import java.sql.{Date, Timestamp}

import slick.lifted.ProvenShape
import slick.sql.SqlProfile.ColumnOption.SqlType

package object database {

  import LedgerWalletDaemon.profile.api._

  class DatabaseVersion(tag: Tag) extends Table[(Int, Timestamp)](tag, "__database__") {
    def version = column[Int]("version", O.PrimaryKey)
    def createdAt = column[Timestamp]("created_at", SqlType("timestamp not null default CURRENT_TIMESTAMP"))
    override def * : ProvenShape[(Int, Timestamp)] = (version, createdAt)
  }
  val databaseVersions = TableQuery[DatabaseVersion]

  class Pools(tag: Tag) extends Table[(String, Timestamp)](tag, "pools") {
    def name = column[String]("name", O.PrimaryKey)
    def createdAt = column[Timestamp]("created_at", SqlType("timestamp not null default CURRENT_TIMESTAMP"))
    def * = (name, createdAt)
  }
  val pools = TableQuery[Pools]

  val Migrations = Map(
    0 -> DBIO.seq(
      (pools.schema ++ databaseVersions.schema).create
    )
  )

}
