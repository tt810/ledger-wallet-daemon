package co.ledger.wallet.daemon.database

import javax.inject.{Inject, Singleton}

import co.ledger.wallet.daemon.Server
import co.ledger.wallet.daemon.services.{ECDSAService, UsersService}
import co.ledger.wallet.daemon.utils.HexUtils
import org.bitcoinj.core.Sha256Hash
import org.spongycastle.util.encoders.Base64

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{Await, Future}
import scala.concurrent.duration._
import scala.util.Try

@Singleton
class DatabaseInitializationRoutine @Inject()(usersService: UsersService, ecdsa: ECDSAService) {

  def perform(): Future[Unit] = {
    insertDemoUsers().flatMap(_ => insertWhitelistedUsers())
  }

  private def insertDemoUsers() = Future {
    if (Server.configuration.hasPath("demo_users")) {
      val usersConfig = Server.configuration.getConfigList("demo_users")
      for (i <- 0 until usersConfig.size()) {
        val userConfig = usersConfig.get(i)
        val username = userConfig.getString("username")
        val password = userConfig.getString("password")
        val auth = s"Basic ${Base64.toBase64String(s"$username:$password".getBytes)}"
        val privKey = Sha256Hash.hash(auth.getBytes)
        val pk = HexUtils.valueOf(Await.result(ecdsa.computePublicKey(privKey), Duration.Inf))
        Try(Await.result(usersService.insertUser(pk), Duration.Inf))
      }
    }
    ()
  }

  private def insertWhitelistedUsers() = Future {
    if (Server.configuration.hasPath("whitelist")){
      val usersConfig = Server.configuration.getConfigList("whitelist")
      for (i <- 0 until usersConfig.size()) {
        val userConfig = usersConfig.get(i)
        val pubKey = userConfig.getString("key")
        val permissions = if (Try(userConfig.getBoolean("account_creation")).getOrElse(false)) PERMISSION_CREATE_USER else 0
        Try(Await.result(usersService.insertUser(pubKey, permissions), Duration.Inf))
      }
    }
    ()
  }

}
