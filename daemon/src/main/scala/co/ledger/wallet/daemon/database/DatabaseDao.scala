package co.ledger.wallet.daemon.database

import java.sql.Timestamp
import java.util.Date
import javax.inject.Singleton

import co.ledger.wallet.daemon.database.DBMigrations.Migrations
import co.ledger.wallet.daemon.exceptions.ResourceAlreadyExistException
import co.ledger.wallet.daemon.utils.HexUtils
import com.twitter.inject.Logging
import slick.jdbc.JdbcBackend.Database

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class DatabaseDao(db: Database)(implicit ec: ExecutionContext) extends Logging {
  import database.Tables.profile.api._
  import database.Tables._
  def migrate(): Future[Unit] = {
    val lastMigrationVersion = databaseVersions.sortBy(_.version.desc).map(_.version).take(1).result.head
    db.run(lastMigrationVersion) recover {
      case _ => -1
    } flatMap { (currentVersion) =>
      debug(s"Current Database Version $currentVersion")
      val maxVersion = Migrations.keys.toArray.sortWith(_ > _).head
      def migrate(version: Int): Future[Unit] = {
        if (version > maxVersion) {
          debug(s"Database Version up to date at version $version")
          Future.successful()
        } else {
          val migrationQ = DBIO.seq(Migrations(version), insertDatabaseVersion(version))
          db.run(migrationQ.transactionally) map { (_) =>
            debug(s"Finish migrating version $version")
            migrate(version + 1)
          }
        }
      }
      migrate(currentVersion + 1)
    }
  }

  def deletePool(poolName: String, userId: Long): Future[Int] = {
    db.run(filterPool(poolName, userId).delete.transactionally).map { int =>
      debug(s"Pool deleted: poolName=$poolName user=$userId returned=$int")
      int
    }
  }

  def getPools: Future[Seq[Pool]] = {
    val rs = db.run(pools.result.transactionally)
    rs.map { rows =>
      debug(s"Pools obtained: size=${rows.size} pools=${rows.map(_.name).toString}")
      rows.map(createPool)
    }
  }

  def getPools(userId: Long): Future[Seq[Pool]] = {
    val query = for {
      p <- pools if p.userId === userId.bind
    } yield p
    db.run(query.result.transactionally).map { rows =>
      debug(s"Pools obtained: user=$userId size=${rows.size} pools=${rows.map(_.name)}")
      rows.map(createPool)
    }
  }

  def getUsers(targetPubKey: Array[Byte]): Future[Seq[User]] = {
    val pubKey = HexUtils.valueOf(targetPubKey)
    db.run(users.filter(_.pubKey === pubKey).result.transactionally)
      .map { rows =>
        debug(s"Users obtained: pubKey=$pubKey size=${rows.size} userIds=${rows.map(_.id.get).toString}")
        rows.map(createUser)
      }
  }


  /**
    * Insert pool if not exists, throw <code>ResourceAlreadyExistException</code> otherwise.
    *
    * @param newPool The new pool instance.
    * @throws ResourceAlreadyExistException If the given pool name and user id are already taken.
    */
  def insertPool(newPool: Pool): Future[Int] = {
    val query = filterPool(newPool.name, newPool.userId).exists.result.flatMap { exists =>
      if (!exists) {
        pools += createPoolRow(newPool)
      } else {
        DBIO.failed(ResourceAlreadyExistException(classOf[Pool], newPool.name))
      }
    }
    db.run(query.transactionally).map { int =>
      debug(s"Pool inserted: poolName=${newPool.name} returned=$int")
      int
    }
  }

  /**
    * Insert user if not exists, throw <code>ResourceAlreadyExistException</code> otherwise.
    *
    * @param newUser The new user instance.
    * @return ResourceAlreadyExistException If the given user pubKey already exists.
    */
  def insertUser(newUser: User): Future[Int] = {
    val query = users.filter(_.pubKey === newUser.pubKey.bind).exists.result.flatMap {(exists) =>
      if (!exists) {
        users += createUserRow(newUser)
      } else {
        DBIO.failed(ResourceAlreadyExistException(classOf[User], newUser.pubKey))
      }
    }
    db.run(query.transactionally).map { int =>
      debug(s"User inserted: pubKey=${newUser.pubKey} returned=$int")
      int
    }
  }

  private def createPoolRow(pool: Pool): PoolRow =
    PoolRow(0, pool.name, new Timestamp(new Date().getTime), pool.configuration, pool.dbBackend, pool.dbConnectString, pool.userId)

  private def createPool(poolRow: PoolRow): Pool =
    Pool(poolRow.name, poolRow.userId, poolRow.configuration, poolRow.dbBackend, poolRow.dbConnectString)

  private def createUserRow(user: User): UserRow =
    UserRow(None, user.pubKey, user.permissions)

  private def createUser(userRow: UserRow): User =
    User(userRow.pubKey, userRow.permissions, userRow.id)

  private def insertDatabaseVersion(version: Int): DBIO[Int] =
    databaseVersions += (version, new Timestamp(new Date().getTime))

  private def filterPool(poolName: String, userId: Long) = {
    pools.filter(pool => pool.userId === userId.bind && pool.name === poolName.bind)
  }

}

case class User(pubKey: String, permissions: Long, id: Option[Long] = None)
case class Pool(name: String, userId: Long, configuration: String, dbBackend: String = "", dbConnectString: String = "")