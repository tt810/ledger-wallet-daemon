package co.ledger.wallet.daemon.database

import java.sql.Timestamp
import java.util.{Date, UUID}
import javax.inject.{Inject, Singleton}

import co.ledger.wallet.daemon.database.DBMigrations.Migrations
import co.ledger.wallet.daemon.exceptions._
import co.ledger.wallet.daemon.utils.HexUtils
import com.twitter.inject.Logging
import slick.jdbc.JdbcBackend.Database
import slick.jdbc.TransactionIsolation

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class DatabaseDao @Inject()(db: Database) extends Logging {
  import database.Tables._
  import database.Tables.profile.api._

  def migrate()(implicit ec: ExecutionContext): Future[Unit] = {
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

  def deletePool(poolName: String, userId: Long)(implicit ec: ExecutionContext): Future[Option[PoolDto]] = {
    val query = filterPool(poolName, userId)
    val action = for {
      result <- query.result.headOption
      _ <- query.delete
    } yield result
    db.run(action.withTransactionIsolation(TransactionIsolation.RepeatableRead)).map { row =>
      row.map(createPool)
    }
  }

  def getPools(userId: Long)(implicit ec: ExecutionContext): Future[Seq[PoolDto]] =
    safeRun(pools.filter(pool => pool.userId === userId.bind).sortBy(_.id.desc).result).map { rows => rows.map(createPool)}

  def getPool(userId: Long, poolName: String)(implicit ec: ExecutionContext): Future[Option[PoolDto]] =
    safeRun(pools.filter(pool => pool.userId === userId && pool.name === poolName).result.headOption).map { row => row.map(createPool)}

  def getUser(targetPubKey: Array[Byte])(implicit ec: ExecutionContext): Future[Option[UserDto]] = {
    getUser(HexUtils.valueOf(targetPubKey))
  }

  def getUser(pubKey: String)(implicit ec: ExecutionContext): Future[Option[UserDto]] =
    safeRun(filterUser(pubKey).result.headOption).map { userRow => userRow.map(createUser)}

  def getUsers()(implicit ec: ExecutionContext): Future[Seq[UserDto]] =
    safeRun(users.result).map { rows => rows.map(createUser(_))}

  def insertPool(newPool: PoolDto)(implicit ec: ExecutionContext): Future[Long] =
    safeRun(filterPool(newPool.name, newPool.userId).exists.result.flatMap { exists =>
      if (!exists) {
        pools.returning(pools.map(_.id)) += createPoolRow(newPool)
      } else {
        DBIO.failed(WalletPoolAlreadyExistException(newPool.name))
      }
    })

  def insertUser(newUser: UserDto)(implicit ec: ExecutionContext): Future[Long] =
    safeRun(filterUser(newUser.pubKey).exists.result.flatMap {(exists) =>
      if (!exists) {
        users.returning(users.map(_.id)) += createUserRow(newUser)
      } else {
        DBIO.failed(UserAlreadyExistException(newUser.pubKey))
      }
    })

  private def safeRun[R](query: DBIO[R])(implicit ec: ExecutionContext): Future[R] =
    db.run(query.transactionally).recoverWith {
      case e: DaemonException => Future.failed(e)
      case others: Throwable => Future.failed(DaemonDatabaseException("Failed to run database query", others))
    }

  private def createPoolRow(pool: PoolDto): PoolRow =
    PoolRow(0, pool.name, pool.userId, new Timestamp(new Date().getTime), pool.configuration, pool.dbBackend, pool.dbConnectString)

  private def createPool(poolRow: PoolRow): PoolDto =
    PoolDto(poolRow.name, poolRow.userId, poolRow.configuration, Option(poolRow.id), poolRow.dbBackend, poolRow.dbConnectString)

  private def createUserRow(user: UserDto): UserRow =
    UserRow(0, user.pubKey, user.permissions)

  private def createUser(userRow: UserRow): UserDto =
    UserDto(userRow.pubKey, userRow.permissions, Option(userRow.id))

  private def insertDatabaseVersion(version: Int): DBIO[Int] =
    databaseVersions += (0, new Timestamp(new Date().getTime))

  private def filterPool(poolName: String, userId: Long) = {
    pools.filter(pool => pool.userId === userId.bind && pool.name === poolName.bind)
  }

  private def filterUser(pubKey: String) = {
    users.filter(_.pubKey === pubKey.bind)
  }

  private def toUUID(str: Option[String]): Option[UUID] = {
    str.map(UUID.fromString(_))
  }

  private def fromUUID(uuid: Option[UUID]): Option[String] = {
    uuid.map(_.toString)
  }

}

case class UserDto(pubKey: String, permissions: Long, id: Option[Long] = None) {
  override def toString: String = s"UserDto(id: $id, pubKey: $pubKey, permissions: $permissions)"
}
case class PoolDto(name: String, userId: Long, configuration: String, id: Option[Long] = None, dbBackend: String = "", dbConnectString: String = "") {
  override def toString: String = s"PoolDto(id: $id, name: $name, userId: $userId, configuration: $configuration, dbBackend: $dbBackend, dbConnectString: $dbConnectString)"
}