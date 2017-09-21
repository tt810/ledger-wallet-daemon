package co.ledger.wallet.daemon.services

import javax.inject.{Inject, Singleton}

import co.ledger.wallet.daemon.DaemonConfiguration
import co.ledger.wallet.daemon.database.User
import co.ledger.wallet.daemon.utils.HexUtils
import com.twitter.inject.Logging
import org.bitcoinj.core.Sha256Hash
import org.spongycastle.util.encoders.Base64

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Future}
import scala.util.Try

@Singleton
class UsersService @Inject()(ecdsa: ECDSAService) extends DaemonService {
  private val dbDao = DatabaseService.dbDao

  def createUser(publicKey: String, permissions: Long = 0): Future[Unit] = {
    info(s"Start to insert user with params: permissions=$permissions pubKey=$publicKey")
    dbDao.insertUser(User(publicKey, permissions)) map {_ =>
      ()
    }
  }

  def createUser(username: String, password: String): Future[Unit] = {
    info(s"Start to insert user with params: username=$username password=XXXX")
    val user = s"Basic ${Base64.toBase64String(s"$username:$password".getBytes)}"
    createUser(user.getBytes)
  }

  private def createUser(user: Array[Byte]): Future[Unit] = {
    val privKey = Sha256Hash.hash(user)
    createUser(pubKey(privKey))
  }

  private def pubKey(privKey: Array[Byte]) = {
    HexUtils.valueOf(Await.result(ecdsa.computePublicKey(privKey), Duration.Inf))
  }

}

object UsersService extends Logging {
  def initialize(usersService: UsersService) = {
    insertDemoUsers(usersService)
    insertWhitelistedUsers(usersService)
  }

  private def insertDemoUsers(usersService: UsersService) = {
    debug("Start insert demo users...")
    val users = DaemonConfiguration.adminUsers
    users.foreach(user =>
      Try(Await.result(usersService.createUser(user.getBytes), Duration.Inf))
    )
  }

  private def insertWhitelistedUsers(usersService: UsersService) = {
    debug("Start insert whitelist users...")
    DaemonConfiguration.whiteListUsers.foreach( user =>
      Try(Await.result(usersService.createUser(user._1, user._2), Duration.Inf)))
  }
}