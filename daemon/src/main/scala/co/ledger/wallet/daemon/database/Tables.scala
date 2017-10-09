package co.ledger.wallet.daemon.database

import java.sql.Timestamp
import java.util.UUID

import co.ledger.wallet.daemon.DaemonConfiguration
import slick.lifted.ProvenShape
import slick.sql.SqlProfile.ColumnOption.SqlType

package database {

  object Tables extends Tables {
    override val profile = DaemonConfiguration.dbProfile
  }
}
trait Tables {
  val profile: slick.jdbc.JdbcProfile

  import profile.api._

  class DatabaseVersion(tag: Tag) extends Table[(Int, Timestamp)](tag, "__database__") {

    def version   = column[Int]("version", O.PrimaryKey)

    def createdAt = column[Timestamp]("created_at", SqlType("timestamp not null default CURRENT_TIMESTAMP"))

    override def * : ProvenShape[(Int, Timestamp)] = (version, createdAt)
  }

  val databaseVersions = TableQuery[DatabaseVersion]

  case class UserRow(id: Long, pubKey: String, permissions: Long, createdAt: Option[Timestamp] = Some(new Timestamp(new java.util.Date().getTime)))

  class Users(tag: Tag) extends Table[UserRow](tag, "users") {
    def id          = column[Long]("id", O.PrimaryKey, O.AutoInc)

    def pubKey      = column[String]("pub_key", O.Unique)

    def createdAt   = column[Timestamp]("created_at", SqlType("timestamp default CURRENT_TIMESTAMP"))

    def permissions = column[Long]("permissions")

    override def *  = (id, pubKey, permissions, createdAt.?) <> (UserRow.tupled, UserRow.unapply)

    def idx         = index("idx_key", pubKey)
  }

  val users = TableQuery[Users]

  case class PoolRow(id: Long, name: String, userId: Long, createdAt: Timestamp, configuration: String, dbBackend: String, dbConnectString: String)

  class Pools(tag: Tag) extends Table[PoolRow](tag, "pools") {
    def id              = column[Long]("id", O.PrimaryKey, O.AutoInc)

    def name            = column[String]("name")

    def userId          = column[Long]("user_id")

    def createdAt       = column[Timestamp]("created_at", SqlType("timestamp default CURRENT_TIMESTAMP"))

    def configuration   = column[String]("configuration", O.Default("{}"))

    def dbBackend       = column[String]("db_backend")

    def dbConnectString = column[String]("db_connect")

    def user            = foreignKey("pool_user_fk", userId, users)(_.id, onUpdate = ForeignKeyAction.Cascade, onDelete = ForeignKeyAction.Cascade)

    def *               = (id, name, userId, createdAt, configuration, dbBackend, dbConnectString) <> (PoolRow.tupled, PoolRow.unapply)
  }

  val pools = TableQuery[Pools]

  case class OperationRow(id: Long, userId: Long, poolId: Long, walletName: String, accountIndex: Int, previous: Option[UUID], offset: Long, batch: Int, next: Option[UUID], createdAt: Timestamp)

  class Operations(tag: Tag) extends Table[OperationRow](tag, "operations") {

    def id            = column[Long]("id", O.PrimaryKey, O.AutoInc)

    def userId        = column[Long]("user_id")

    def poolId      = column[Long]("pool_id")

    def walletName    = column[String]("wallet_name")

    def accountIndex  = column[Int]("account_index")

    def previous         = column[Option[UUID]]("previous_cursor", O.Unique)

    def offset        = column[Long]("offset")

    def batch         = column[Int]("batch")

    def next     = column[Option[UUID]]("next_cursor", O.Unique)

    def createdAt     = column[Timestamp]("created_at", SqlType("timestamp default CURRENT_TIMESTAMP"))

    def pool          = foreignKey("op_pool_fk", poolId, pools)(_.id, onUpdate = ForeignKeyAction.Cascade, onDelete = ForeignKeyAction.Cascade)

    def user          = foreignKey("op_user_fk", userId, users)(_.id, onUpdate = ForeignKeyAction.Cascade, onDelete = ForeignKeyAction.Cascade)

    override def *    = (id, userId, poolId, walletName, accountIndex, previous, offset, batch, next, createdAt) <> (OperationRow.tupled, OperationRow.unapply)

    def idx           = index("idx_user_pool_wallet_account", (userId, poolId, walletName, accountIndex))
  }

  val operations = TableQuery[Operations]
}
