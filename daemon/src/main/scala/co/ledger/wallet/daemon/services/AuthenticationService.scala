package co.ledger.wallet.daemon.services

import java.nio.charset.StandardCharsets
import java.util.Date
import javax.inject.{Inject, Singleton}

import co.ledger.wallet.daemon.Server
import co.ledger.wallet.daemon.database.User
import co.ledger.wallet.daemon.services.AuthenticationService.{AuthenticationFailedException, AuthentifiedUser, AuthentifiedUserContext}
import co.ledger.wallet.daemon.utils.HexUtils
import com.twitter.finagle.http.Request
import com.twitter.util.Future
import org.bitcoinj.core.Sha256Hash

import scala.concurrent.ExecutionContext.Implicits.global
import co.ledger.wallet.daemon.utils._

import scala.concurrent.Await
import scala.concurrent.duration.Duration

@Singleton
class AuthenticationService @Inject()(databaseService: DatabaseService, ecdsa: ECDSAService) {
  import co.ledger.wallet.daemon.Server.profile.api._
  import co.ledger.wallet.daemon.services.AuthenticationService.AuthContextContext._
  import co.ledger.wallet.daemon.database._

  def authorize(request: Request): Future[Unit] = {
    databaseService.database flatMap {(db) =>
      db.run(users.filter(_.pubKey === HexUtils.valueOf(request.authContext.pubKey)).result) map {(users) =>
        if (users.isEmpty)
          throw AuthenticationFailedException()
        users.head
      }
    } flatMap {user =>
      val time = request.authContext.time
      val date = new Date(time * 1000)
      val now = new Date()
      if (Math.abs(now.getTime - date.getTime) > _tokenDuration) {
        throw AuthenticationFailedException()
      }
      val message = Sha256Hash.hash(s"LWD: $time\n".getBytes(StandardCharsets.US_ASCII))
      val signed = request.authContext.signedMessage
      ecdsa.verify(message, signed, request.authContext.pubKey).map({(success) =>
        if (!success)
          throw AuthenticationFailedException()
        AuthentifiedUserContext.setUser(request, user)
        ()
      })
    } asTwitter()
  }

  private val _tokenDuration = {
    if (Server.configuration.hasPath("authentication.token_duration"))
      Server.configuration.getInt("authentication.token_duration") * 1000
    else
      30000
  }

}

object AuthenticationService {

  case class AuthenticationFailedException() extends Exception("Unable to authenticate user with those credentials")
  case class AuthContext(pubKey: Array[Byte], time: Long, signedMessage: Array[Byte])
  object AuthContextContext {
    private val AuthContextField = Request.Schema.newField[AuthContext]()
    implicit class AuthContextContextSyntax(val request: Request) extends AnyVal {
      def authContext: AuthContext = request.ctx(AuthContextField)
    }
    def setContext(request: Request, context: AuthContext): Unit = {
      request.ctx.update(AuthContextField, context)
    }
  }

  case class AuthentifiedUser(get: User)
  object AuthentifiedUserContext {
    private val UserField = Request.Schema.newField[AuthentifiedUser]()

    implicit class UserContextSyntax(val request: Request) extends AnyVal {
      def user: AuthentifiedUser = request.ctx(UserField)
    }
    def setUser(request: Request, user: User): Unit = request.ctx.update[AuthentifiedUser](UserField, AuthentifiedUser(user))
  }

}