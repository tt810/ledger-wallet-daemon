package co.ledger.wallet.daemon.database

import java.sql.Timestamp
import java.util.Date
import java.util.concurrent.Executors
import javax.inject.{Inject, Singleton}

import co.ledger.wallet.daemon.async.SerialExecutionContext
import co.ledger.wallet.daemon.database.DBMigrations.Migrations
import co.ledger.wallet.daemon.exceptions._
import co.ledger.wallet.daemon.utils.HexUtils
import com.twitter.inject.Logging
import slick.jdbc.JdbcBackend.Database

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

@Singleton
class DatabaseDao @Inject()(db: Database)(implicit ec: ExecutionContext) extends Logging {
  import database.Tables.profile.api._
  import database.Tables._

  def migrate(): Future[Unit] = {
    info("Start database migration")
    val lastMigrationVersion = databaseVersions.sortBy(_.version.desc).map(_.version).take(1).result.head
    db.run(lastMigrationVersion.transactionally) recover {
      case _ => -1
    } flatMap { currentVersion =>
      {
        debug(s"Current database version at ${currentVersion}")
        val maxVersion = Migrations.keys.toArray.sortWith(_ > _).head

        def migrate(version: Int, maxVersion: Int): Future[Unit] = {
          if(version > maxVersion) {
            debug(s"Database version up to date at $version")
            Future.successful()
          } else {
            debug(s"Migrating version $version / $maxVersion")
            val rollbackMigrate = DBIO.seq(Migrations(version), insertDatabaseVersion(version))
            db.run(rollbackMigrate.transactionally).flatMap { _ =>
              debug(s"version $version / $maxVersion migration done")
              migrate(version + 1, maxVersion)
            }
          }
        }
        migrate(currentVersion + 1, maxVersion)
      }
    }
  }

  def deletePool(poolName: String, userId: Long): Future[Int] = {
    safeRun(filterPool(poolName, userId).delete).map { int =>
      debug(s"Pool deleted: poolName=$poolName user=$userId returned=$int")
      int
    }
  }

  def getPools(userId: Long): Future[Seq[Pool]] = {
    val query = pools.filter(pool => pool.userId === userId.bind).sortBy(_.id.desc)
    safeRun(query.result.transactionally).map { rows =>
      debug(s"Pools obtained: user=$userId size=${rows.size} pools=${rows.map(_.name)}")
      rows.map(createPool)
    }
  }

  def getUser(targetPubKey: Array[Byte]): Future[Option[User]] = {
    val pubKey = HexUtils.valueOf(targetPubKey)
    safeRun(filterUser(pubKey).result).map { rows =>
      debug(s"Users obtained: pubKey=$pubKey size=${rows.size} userIds=${rows.map(_.id.get).toString}")
      if(rows.isEmpty) None
      else Option(createUser(rows.head))
    }
  }

  def getUsers(): Future[Seq[User]] = {
    val query = users.result
    safeRun(query).map { rows =>
      debug(s"Users obtained: size=${rows.size}")
      rows.map(createUser(_))
    }
  }

  def insertPool(newPool: Pool): Future[Int] = {
    val query = filterPool(newPool.name, newPool.userId).exists.result.flatMap { exists =>
      if (!exists) {
        pools += createPoolRow(newPool)
      } else {
        DBIO.failed(WalletPoolAlreadyExistException(newPool.name))
      }
    }
    safeRun(query).map { int =>
      debug(s"Pool inserted: poolName=${newPool.name} returned=$int")
      int
    }
  }

  def insertUser(newUser: User): Future[Int] = {
    val query = filterUser(newUser.pubKey).exists.result.flatMap {(exists) =>
      if (!exists) {
        users += createUserRow(newUser)
      } else {
        DBIO.failed(UserAlreadyExistException(newUser.pubKey))
      }
    }
    safeRun(query).map { int =>
      debug(s"User inserted: pubKey=${newUser.pubKey} returned=$int")
      int
    }
  }

  private def safeRun[R](query: DBIO[R]): Future[R] = {
    db.run(query.transactionally).recoverWith {
      case e: DaemonException => Future.failed(e)
      case others: Throwable => Future.failed(DaemonDatabaseException("Failed to run database query", others))
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
    databaseVersions += (0, new Timestamp(new Date().getTime))

  private def filterPool(poolName: String, userId: Long) = {
    pools.filter(pool => pool.userId === userId.bind && pool.name === poolName.bind)
  }

  private def filterUser(pubKey: String) = {
    users.filter(_.pubKey === pubKey.bind)
  }

}

case class User(pubKey: String, permissions: Long, id: Option[Long] = None)
case class Pool(name: String, userId: Long, configuration: String, dbBackend: String = "", dbConnectString: String = "")