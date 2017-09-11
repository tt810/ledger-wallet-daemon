package co.ledger.wallet.daemon
import java.sql.{Timestamp}
import slick.lifted.ProvenShape
import slick.sql.SqlProfile.ColumnOption.SqlType

package object database {

  import Server.profile.api._

  val PERMISSION_CREATE_USER = 0x01

  class DatabaseVersion(tag: Tag) extends Table[(Int, Timestamp)](tag, "__database__") {
    def version = column[Int]("version", O.PrimaryKey)
    def createdAt = column[Timestamp]("created_at", SqlType("timestamp not null default CURRENT_TIMESTAMP"))
    override def * : ProvenShape[(Int, Timestamp)] = (version, createdAt)
  }
  val databaseVersions = TableQuery[DatabaseVersion]

  case class User(id: Option[Long], pubKey: String, permissions: Long, createdAt: Option[Timestamp] = Some(new Timestamp(new java.util.Date().getTime)))
  class Users(tag: Tag) extends Table[User](tag, "users") {
    def id = column[Long]("id", O.PrimaryKey, O.AutoInc)
    def pubKey = column[String]("pub_key", O.Default(null))
    def createdAt = column[Timestamp]("created_at", SqlType("timestamp default CURRENT_TIMESTAMP"))
    def permissions = column[Long]("permissions")
    override def * = (id.?, pubKey, permissions, createdAt.?) <> (User.tupled, User.unapply)
  }
  val users = TableQuery[Users]

  case class Pool(id: Long, name: String, createdAt: Timestamp, configuration: String, dbBackend: String, dbConnectString: String, userId: Long)
  class Pools(tag: Tag) extends Table[Pool](tag, "pools") {
    def id = column[Long]("id", O.PrimaryKey, O.AutoInc)
    def name = column[String]("name")
    def createdAt = column[Timestamp]("created_at", SqlType("timestamp default CURRENT_TIMESTAMP"))
    def configuration = column[String]("configuration", O.Default("{}"))
    def dbBackend = column[String]("db_backend")
    def dbConnectString = column[String]("db_connect")
    def userId = column[Long]("user_id")
    def user = foreignKey("user_fk", userId, users)(_.id, onUpdate = ForeignKeyAction.Cascade, onDelete = ForeignKeyAction.Cascade)
    def * = (id, name, createdAt, configuration, dbBackend, dbConnectString, userId) <> (Pool.tupled, Pool.unapply)
  }
  val pools = TableQuery[Pools]

  val Migrations = Map(
    0 -> DBIO.seq(
      (users.schema ++ pools.schema ++ databaseVersions.schema).create
    )
  )

}
