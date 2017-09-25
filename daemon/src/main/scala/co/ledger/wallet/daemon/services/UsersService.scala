package co.ledger.wallet.daemon.services

import javax.inject.{Inject, Singleton}

import co.ledger.wallet.daemon.DaemonConfiguration
import co.ledger.wallet.daemon.database.User
import co.ledger.wallet.daemon.utils.HexUtils
import com.twitter.inject.Logging
import org.bitcoinj.core.Sha256Hash
import org.spongycastle.util.encoders.Base64

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

@Singleton
class UsersService @Inject()(ecdsa: ECDSAService) extends DaemonService {

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
    pubKey(privKey).flatMap { publicKey =>
      createUser(publicKey)
    }
  }

  private def pubKey(privKey: Array[Byte]): Future[String] = {
    ecdsa.computePublicKey(privKey).map {pubKey =>
      HexUtils.valueOf(pubKey)
    }
  }

  private val dbDao = DatabaseService.dbDao
}

object UsersService extends Logging {

  def initialize(usersService: UsersService): Future[Unit] = Future {
    insertDemoUsers(usersService)
    insertWhitelistedUsers(usersService)
  }

  private def insertDemoUsers(usersService: UsersService) = Future.sequence {
    debug("Start insert demo users...")
    DaemonConfiguration.adminUsers.map { user =>
      usersService.createUser(user.getBytes())
    }
  }.onComplete {
    case success => debug("Finish inserting demo users")
  }


  private def insertWhitelistedUsers(usersService: UsersService) = Future.sequence {
    debug("Start insert whitelist users...")
    DaemonConfiguration.whiteListUsers.map { user =>
      usersService.createUser(user._1, user._2)
    }
  }.onComplete {
    case success => debug("Finish inserting whitelist users")
  }

}