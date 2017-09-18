package co.ledger.wallet.daemon.database.slick

import java.sql.{Date, Timestamp}
import javax.inject.{Inject, Singleton}

import co.ledger.wallet.daemon.database.{DatabaseDAO, Pool}
import slick.jdbc.JdbcBackend.Database

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class SlickDatabaseDAO @Inject()(db: Database)(implicit ec: ExecutionContext) extends DatabaseDAO with Tables {
  override val profile = slick.jdbc.H2Profile
  import profile.api._

  override def deletePool(poolName: String, userId: Long): Future[Int] = ???

  override def getPools() = ???

  override def getPools(userId: Long) = ???

  override def getUsers(publicKey: Array[Byte]) = ???

  override def insertIfNotExist(poolName: String, userId: Long, configuration: String) = ???

  override def insertIfNotExist(publicKey: String, permissions: Long) = ???


}
