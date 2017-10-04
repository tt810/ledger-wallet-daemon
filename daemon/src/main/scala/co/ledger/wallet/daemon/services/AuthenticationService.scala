package co.ledger.wallet.daemon.services

import java.nio.charset.StandardCharsets
import java.util.Date
import javax.inject.{Inject, Singleton}

import co.ledger.wallet.daemon.DaemonConfiguration
import co.ledger.wallet.daemon.database.{DaemonCache, User}
import co.ledger.wallet.daemon.services.AuthenticationService.{AuthenticationFailedException, AuthentifiedUserContext}
import com.twitter.finagle.http.Request
import com.twitter.util.Future
import org.bitcoinj.core.Sha256Hash
import co.ledger.wallet.daemon.utils._

import scala.concurrent.ExecutionContext

@Singleton
class AuthenticationService @Inject()(daemonCache: DaemonCache, ecdsa: ECDSAService) extends DaemonService {
  import co.ledger.wallet.daemon.services.AuthenticationService.AuthContextContext._

  def authorize(request: Request)(implicit ec: ExecutionContext): Future[Unit] = {
    try {
      val pubKey = request.authContext.pubKey
      daemonCache.getUserDirectlyFromDB(pubKey) map { (usr) =>
        if (usr.isEmpty)
          throw AuthenticationFailedException("User doesn't exist")
        usr.get
      } flatMap {user =>
        debug(LogMsgMaker.newInstance("Authorizing request")
          .append("user_pub_key", user.pubKey)
          .toString())
        val time = request.authContext.time
        val date = new Date(time * 1000)
        val now = new Date()
        if (Math.abs(now.getTime - date.getTime) > DaemonConfiguration.authTokenDuration) {
          throw AuthenticationFailedException("Authentication token expired")
        }
        val message = Sha256Hash.hash(s"LWD: $time\n".getBytes(StandardCharsets.US_ASCII))
        val signed = request.authContext.signedMessage
        ecdsa.verify(message, signed, request.authContext.pubKey).map({(success) =>
          if (!success)
            throw AuthenticationFailedException("User not authorized")
          AuthentifiedUserContext.setUser(request, user)
          ()
        })
      } asTwitter()
    } catch {
      case e: IllegalStateException => throw new AuthenticationFailedException("Missing authorization header")
    }
  }
}

object AuthenticationService {

  case class AuthenticationFailedException(msg: String) extends Exception(msg)
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