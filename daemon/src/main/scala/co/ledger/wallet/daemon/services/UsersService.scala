package co.ledger.wallet.daemon.services

import javax.inject.{Inject, Singleton}

import co.ledger.wallet.daemon.DaemonConfiguration
import co.ledger.wallet.daemon.database.{DefaultDaemonCache, User}
import co.ledger.wallet.daemon.utils.HexUtils
import com.twitter.inject.Logging
import org.bitcoinj.core.Sha256Hash
import org.spongycastle.util.encoders.Base64
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class UsersService @Inject()(daemonCache: DefaultDaemonCache, ecdsa: ECDSAService) extends DaemonService {

  def createUser(publicKey: String, permissions: Long = 0): Future[Int] = {
    info(LogMsgMaker.newInstance("Create user with params")
      .append("pubKey", publicKey)
      .append("permissions", permissions)
      .toString())
    daemonCache.createUser(User(publicKey, permissions))
  }

  def createUser(username: String, password: String)(implicit ec: ExecutionContext): Future[Int] = {
    info(LogMsgMaker.newInstance("Create user with params")
      .append("username", username)
      .append("password", if(password.isEmpty) "XXXXXXX" else "")
      .toString())
    val user = s"Basic ${Base64.toBase64String(s"$username:$password".getBytes)}"
    createUser(user.getBytes)
  }

  private def createUser(user: Array[Byte])(implicit ec: ExecutionContext): Future[Int] = {
    val privKey = Sha256Hash.hash(user)
    pubKey(privKey).flatMap { publicKey =>
      createUser(publicKey)
    }
  }

  private def pubKey(privKey: Array[Byte])(implicit ec: ExecutionContext): Future[String] = {
    ecdsa.computePublicKey(privKey).map {pubKey =>
      HexUtils.valueOf(pubKey)
    }
  }

}

object UsersService extends Logging {

  def initialize(usersService: UsersService)(implicit ec: ExecutionContext): Future[Unit] = {
    insertDemoUsers(usersService).flatMap { _ =>
      insertWhitelistedUsers(usersService).map { _ =>
        ()
      }
    }
  }

  private def insertDemoUsers(usersService: UsersService)(implicit ec: ExecutionContext) = Future.sequence {
    DaemonConfiguration.adminUsers.map { user =>
      debug(LogMsgMaker.newInstance("Insert demo user").toString())
      usersService.createUser(user.getBytes())
    }
  }

  private def insertWhitelistedUsers(usersService: UsersService)(implicit ec: ExecutionContext) = Future.sequence {
    DaemonConfiguration.whiteListUsers.map { user =>
      debug(LogMsgMaker.newInstance("Insert whitelist user").toString())
      usersService.createUser(user._1, user._2)
    }
  }

}