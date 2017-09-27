package co.ledger.wallet.daemon.database

import java.sql.Timestamp

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
    def version = column[Int]("version", O.PrimaryKey)

    def createdAt = column[Timestamp]("created_at", SqlType("timestamp not null default CURRENT_TIMESTAMP"))

    override def * : ProvenShape[(Int, Timestamp)] = (version, createdAt)
  }

  val databaseVersions = TableQuery[DatabaseVersion]

  case class UserRow(id: Option[Long], pubKey: String, permissions: Long, createdAt: Option[Timestamp] = Some(new Timestamp(new java.util.Date().getTime)))

  class Users(tag: Tag) extends Table[UserRow](tag, "users") {
    def id = column[Long]("id", O.PrimaryKey, O.AutoInc)

    def pubKey = column[String]("pub_key", O.Unique)

    def createdAt = column[Timestamp]("created_at", SqlType("timestamp default CURRENT_TIMESTAMP"))

    def permissions = column[Long]("permissions")

    override def * = (id.?, pubKey, permissions, createdAt.?) <> (UserRow.tupled, UserRow.unapply)
  }

  val users = TableQuery[Users]

  case class PoolRow(id: Long, name: String, createdAt: Timestamp, configuration: String, dbBackend: String, dbConnectString: String, userId: Long)

  class Pools(tag: Tag) extends Table[PoolRow](tag, "pools") {
    def id = column[Long]("id", O.PrimaryKey, O.AutoInc)

    def name = column[String]("name")

    def userId = column[Long]("user_id")

    def createdAt = column[Timestamp]("created_at", SqlType("timestamp default CURRENT_TIMESTAMP"))

    def configuration = column[String]("configuration", O.Default("{}"))

    def dbBackend = column[String]("db_backend")

    def dbConnectString = column[String]("db_connect")

    def user = foreignKey("user_fk", userId, users)(_.id, onUpdate = ForeignKeyAction.Cascade, onDelete = ForeignKeyAction.Cascade)

    def * = (id, name, createdAt, configuration, dbBackend, dbConnectString, userId) <> (PoolRow.tupled, PoolRow.unapply)
  }

  val pools = TableQuery[Pools]
}