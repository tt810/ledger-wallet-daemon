package co.ledger.wallet.daemon.database

/**
  * Object keeps database migration history
  */
object DBMigrations {
  import database.Tables.profile.api._
  import database.Tables._
  val Migrations = Map(
    0 -> (users.schema ++ pools.schema ++ databaseVersions.schema).create
  )
}
