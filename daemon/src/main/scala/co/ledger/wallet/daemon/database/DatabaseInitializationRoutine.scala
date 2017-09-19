package co.ledger.wallet.daemon.database

import javax.inject.{Inject, Singleton}

import co.ledger.wallet.daemon.DaemonConfiguration
import co.ledger.wallet.daemon.services.{ECDSAService, UsersService}
import co.ledger.wallet.daemon.utils.HexUtils
import org.bitcoinj.core.Sha256Hash

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{Await, Future}
import scala.concurrent.duration._
import scala.util.Try

@Singleton
class DatabaseInitializationRoutine @Inject()(usersService: UsersService, ecdsa: ECDSAService) {

  def perform(): Unit = {
    insertDemoUsers()
    insertWhitelistedUsers()
  }

  private def insertDemoUsers() = {
    val users = DaemonConfiguration.adminUsers
    users.foreach(user => {
      val privKey = Sha256Hash.hash(user.getBytes);
      val pk = HexUtils.valueOf(Await.result(ecdsa.computePublicKey(privKey), Duration.Inf));
      Try(Await.result(usersService.insertUser(pk), Duration.Inf))
    })
  }

  private def insertWhitelistedUsers() = {
    val users = DaemonConfiguration.whiteListUsers
    users.foreach(user => {
      Try(Await.result(usersService.insertUser(user._1, user._2), Duration.Inf))
    })
  }

}
