package co.ledger.wallet.daemon.database

import java.sql.Timestamp

import scala.concurrent.Future

trait DatabaseDAO {

  def deletePool(poolName: String, userId: Long): Future[Int]

  def getPools(): Future[Seq[Pool]]
  def getPools(userId: Long): Future[Seq[Pool]]

  def getUsers(publicKey: Array[Byte]): Future[Seq[User]]

  def insertIfNotExist(poolName: String, userId: Long, configuration: String): Future[Pool]
  def insertIfNotExist(publicKey: String, permissions: Long): Future[User]

}

//case class User(id: Option[Long], pubKey: String, permissions: Long, createdAt: Option[Timestamp] = Some(new Timestamp(new java.util.Date().getTime)))
//case class Pool(id: Long, name: String, createdAt: Timestamp, configuration: String, dbBackend: String, dbConnectString: String, userId: Long)

