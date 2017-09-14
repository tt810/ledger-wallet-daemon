package co.ledger.wallet.daemon.database

import co.ledger.wallet.daemon.Server

/**
  * Object keeps database migration history
  */
object DBMigrations {
  import Server.profile.api._

  val Migrations = Map(
    0 -> (users.schema ++ pools.schema ++ databaseVersions.schema).create
  )
}
