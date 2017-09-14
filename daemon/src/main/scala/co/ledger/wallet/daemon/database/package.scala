package co.ledger.wallet.daemon
import java.sql.Timestamp
import java.util.Date

import co.ledger.wallet.daemon.exceptions.ResourceAlreadyExistException
import co.ledger.wallet.daemon.utils.HexUtils
import slick.lifted.ProvenShape
import slick.sql.SqlProfile.ColumnOption.SqlType

import scala.concurrent.ExecutionContext.Implicits.global

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

  def createPool(poolName: String, userId: Long, configuration: String): Pool =
    Pool(0, poolName, new Timestamp(new Date().getTime), configuration, "", "", userId)

  def createUser(publicKey: String, permissions: Long): User =
    User(None, publicKey, permissions)

  def deletePool(poolName: String, userId: Long): DBIO[Int] = {
    filterPool(poolName, userId).delete
  }

  def getLastMigrationVersion(): DBIO[Int] =
    databaseVersions.sortBy(_.version.desc).map(_.version).take(1).result.head

  def getPools: DBIO[Seq[Pool]] =
    pools.result

  def getPools(userId: Long): DBIO[Seq[Pool]] = {
    val query = for {
      p <- database.pools if p.userId === userId.bind
    } yield p
    query.result
  }

  def getUsers(targetPubKey: Array[Byte]): DBIO[Seq[User]] =
    users.filter(_.pubKey === HexUtils.valueOf(targetPubKey)).result

  def insertDatabaseVersion(version: Int): DBIO[Int] =
    databaseVersions += (version, new Timestamp(new Date().getTime))

  /**
    * Insert pool if not exists, throw <code>ResourceAlreadyExistException</code> otherwise.
    *
    * @param newPool The new pool instance.
    * @throws ResourceAlreadyExistException If the given pool name and user id are already taken.
    */
  def insertPool(newPool: Pool): DBIO[Int] = {
    filterPool(newPool.name, newPool.userId).exists.result.flatMap { exists =>
      if (!exists) {
        pools += newPool
      } else {
        DBIO.failed(ResourceAlreadyExistException(classOf[Pool], newPool.name))
      }
    }
  }

  /**
    * Insert user if not exists, throw <code>ResourceAlreadyExistException</code> otherwise.
    *
    * @param newUser The new user instance.
    * @return ResourceAlreadyExistException If the given user pubKey already exists.
    */
  def insertUser(newUser: User): DBIO[Int] = {
    users.filter(_.pubKey === newUser.pubKey.bind).exists.result.flatMap {(exists) =>
      if (!exists) {
        users += newUser
      } else {
        DBIO.failed(ResourceAlreadyExistException(classOf[User], newUser.pubKey))
      }
    }
  }

  private def filterPool(poolName: String, userId: Long) = {
    pools.filter(pool => pool.userId === userId.bind && pool.name === poolName.bind)
  }

}
