package co.ledger.wallet.daemon.database

import java.sql.Timestamp
import java.util.Date
import javax.inject.{Inject, Singleton}

import co.ledger.wallet.daemon.database.DBMigrations.Migrations
import co.ledger.wallet.daemon.exceptions._
import co.ledger.wallet.daemon.services.LogMsgMaker
import co.ledger.wallet.daemon.utils.HexUtils
import com.twitter.inject.Logging
import slick.jdbc.JdbcBackend.Database

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class DatabaseDao @Inject()(db: Database) extends Logging {
  import database.Tables.profile.api._
  import database.Tables._

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

  def deletePool(poolName: String, userId: Long)(implicit ec: ExecutionContext): Future[Int] = {
    safeRun(filterPool(poolName, userId).delete).map { int =>
      debug(LogMsgMaker.newInstance("Daemon wallet pool deleted")
        .append("pool_name", poolName)
        .append("user_id", userId)
        .append("result_row", int)
        .toString())
      int
    }
  }

  def getPools(userId: Long)(implicit ec: ExecutionContext): Future[Seq[Pool]] = {
    val query = pools.filter(pool => pool.userId === userId.bind).sortBy(_.id.desc)
    safeRun(query.result).map { rows =>
      debug(LogMsgMaker.newInstance("Daemon wallet pools retrieved")
        .append("user_id", userId)
        .append("result_row", rows.size)
        .append("result", rows.map(_.name))
        .toString())
      rows.map(createPool)
    }
  }

  def getPool(userId: Long, poolName: String)(implicit ec: ExecutionContext): Future[Option[Pool]] = {
    val query = pools.filter(pool => pool.userId === userId && pool.name === poolName).result.headOption
    safeRun(query).map { row =>
      debug(LogMsgMaker.newInstance("Daemon wallet pool retrieved")
        .append("user_id", userId)
        .append("pool_name", poolName)
        .append("result", row)
        .toString())
      row.map(createPool)
    }
  }

  def getUser(targetPubKey: Array[Byte])(implicit ec: ExecutionContext): Future[Option[User]] = {
    getUser(HexUtils.valueOf(targetPubKey))
  }

  def getUser(pubKey: String)(implicit ec: ExecutionContext): Future[Option[User]] = {
    safeRun(filterUser(pubKey).result).map { rows =>
      debug(LogMsgMaker.newInstance("Daemon user retrieved")
        .append("pub_key", pubKey)
        .append("result_row", rows.size)
        .append("result", rows.map(_.id))
        .toString())
      if(rows.isEmpty) None
      else Option(createUser(rows.head))
    }
  }

  def getUsers()(implicit ec: ExecutionContext): Future[Seq[User]] = {
    val query = users.result
    safeRun(query).map { rows =>
      debug(LogMsgMaker.newInstance("Daemon users retrieved")
        .append("result_row", rows.size)
        .append("result", rows.map(_.id))
        .toString())
      rows.map(createUser(_))
    }
  }

  def getFirstAccountOperation(nextOpUId: Option[String], userId: Long, poolId: Long, walletName: String, accountIndex: Int)(implicit ec: ExecutionContext): Future[Option[Operation]] = {
    val query = operations.filter { op =>
      op.nextOpUId === nextOpUId && op.userId === userId && op.poolId === poolId && op.walletName === walletName && op.accountIndex === accountIndex
    }.sortBy(_.id).result
    safeRun(query).map { ops =>
      debug(LogMsgMaker.newInstance("Daemon account operation retrieved")
        .append("next_op_uid", nextOpUId)
        .append("user_id", userId)
        .append("pool_id", poolId)
        .append("wallet_name", walletName)
        .append("account_index", accountIndex)
        .append("result_row", ops.size)
        .append("result", ops.map(_.id))
        .toString())
      ops.headOption.map(createOperation(_))
    }
  }

  def getFirstAccountOperation(userId: Long, poolId: Long, walletName: String, accountIndex: Int)(implicit ec: ExecutionContext): Future[Option[Operation]] = {
    val query = operations.filter { op =>
      op.userId === userId && op.poolId === poolId && op.walletName === walletName && op.accountIndex === accountIndex
    }.sortBy(_.id).result
    safeRun(query).map { ops =>
      debug(LogMsgMaker.newInstance("Daemon account operation retrieved")
        .append("user_id", userId)
        .append("pool_id", poolId)
        .append("wallet_name", walletName)
        .append("account_index", accountIndex)
        .append("result_row", ops.size)
        .append("result", ops.map(_.id))
        .toString())
      ops.headOption.map(createOperation(_))
    }
  }

  def insertOperation(operation: Operation)(implicit ec: ExecutionContext): Future[Int] = {
    val query = operations += createOperationRow(operation)
    safeRun(query).map { int =>
      debug(LogMsgMaker.newInstance("Daemon operation inserted")
        .append("operation", operation)
        .append("result", int)
        .toString())
      int
    }

  }

  def updateOperation(id: Long, opUId: String, offset: Long, batch: Int, nextOpUId: Option[String])(implicit ec: ExecutionContext): Future[Int] = {
    val query = operations.filter(_.id === id)
      .map(op => (op.opUId, op.offset, op.batch, op.nextOpUId, op.updatedAt))
      .update(opUId, offset, batch, nextOpUId, new Timestamp(new Date().getTime))

    safeRun(query).map { int =>
      debug(LogMsgMaker.newInstance("Daemon operation updated")
        .append("id", id)
        .append("op_uid", opUId)
        .append("offset", offset)
        .append("batch", batch)
        .append("next_op_uid", nextOpUId)
        .toString())
      int
    }
  }

  def insertPool(newPool: Pool)(implicit ec: ExecutionContext): Future[Int] = {
    val query = filterPool(newPool.name, newPool.userId).exists.result.flatMap { exists =>
      if (!exists) {
        pools += createPoolRow(newPool)
      } else {
        DBIO.failed(WalletPoolAlreadyExistException(newPool.name))
      }
    }
    safeRun(query).map { int =>
      debug(LogMsgMaker.newInstance("Daemon wallet pool inserted")
        .append("wallet_pool", newPool)
        .append("result", int)
        .toString())
      int
    }
  }

  def insertUser(newUser: User)(implicit ec: ExecutionContext): Future[Int] = {
    val query = filterUser(newUser.pubKey).exists.result.flatMap {(exists) =>
      if (!exists) {
        users += createUserRow(newUser)
      } else {
        DBIO.failed(UserAlreadyExistException(newUser.pubKey))
      }
    }
    safeRun(query).map { int =>
      debug(LogMsgMaker.newInstance("Daemon user inserted")
        .append("user", newUser)
        .append("result", int)
        .toString())
      int
    }
  }

  private def safeRun[R](query: DBIO[R])(implicit ec: ExecutionContext): Future[R] = {
    db.run(query.transactionally).recoverWith {
      case e: DaemonException => Future.failed(e)
      case others: Throwable => Future.failed(DaemonDatabaseException("Failed to run database query", others))
    }
  }

  private def createOperationRow(operation: Operation): OperationRow = {
    val currentTime = new Timestamp(new Date().getTime)
    OperationRow(0, operation.userId, operation.poolId, operation.walletName, operation.accountIndex, operation.opUId, operation.offset, operation.batch, operation.nextOpUId, currentTime, currentTime)
  }

  private def createOperation(opRow: OperationRow): Operation = {
    Operation(opRow.userId, opRow.poolId, opRow.walletName, opRow.accountIndex, opRow.opUId, opRow.offset, opRow.batch, opRow.nextOpUId, Option(opRow.id))
  }

  private def createPoolRow(pool: Pool): PoolRow =
    PoolRow(0, pool.name, pool.userId, new Timestamp(new Date().getTime), pool.configuration, pool.dbBackend, pool.dbConnectString)

  private def createPool(poolRow: PoolRow): Pool =
    Pool(poolRow.name, poolRow.userId, poolRow.configuration, Option(poolRow.id), poolRow.dbBackend, poolRow.dbConnectString)

  private def createUserRow(user: User): UserRow =
    UserRow(0, user.pubKey, user.permissions)

  private def createUser(userRow: UserRow): User =
    User(userRow.pubKey, userRow.permissions, Option(userRow.id))

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
case class Pool(name: String, userId: Long, configuration: String, id: Option[Long] = None, dbBackend: String = "", dbConnectString: String = "")
case class Operation(userId: Long, poolId: Long, walletName: String, accountIndex: Int, opUId: String, offset: Long, batch: Int, nextOpUId: Option[String], id: Option[Long] = None)