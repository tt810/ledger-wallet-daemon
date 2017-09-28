package co.ledger.wallet.daemon.filters

import javax.inject.Inject

import co.ledger.wallet.daemon.models.AccountDerivation
import com.twitter.finagle.{Service, SimpleFilter}
import com.twitter.finagle.http.{Request, Response}
import com.twitter.finatra.http.exceptions.BadRequestException
import com.twitter.finatra.http.internal.marshalling.MessageBodyManager

class AccountCreationFilter @Inject()(messageBodyManager: MessageBodyManager) extends SimpleFilter[Request, Response] {

  override def apply(request: Request, service: Service[Request, Response]) = {
    val accountCreationBody = messageBodyManager.read[AccountDerivation](request)
    accountCreationBody.derivations.foreach { derivation =>
      if(derivation.pubKey == None) throw new BadRequestException("derivations.pub_key: field is required")
      else if (derivation.chainCode == None) throw new BadRequestException("derivations.chain_code: field is required")
    }
    AccountCreationContext.setAccountCreationBody(request, accountCreationBody)
    service(request)
  }
}

object AccountCreationContext {
  private val AccountCreationField = Request.Schema.newField[AccountDerivation]()

  implicit class AccountCreationContextSyntax(val request: Request) extends AnyVal {
    def accountCreationBody: AccountDerivation = request.ctx(AccountCreationField)
  }

  def setAccountCreationBody(request: Request, accountCreationBody: AccountDerivation): Unit = {
    request.ctx.update(AccountCreationField, accountCreationBody)
  }
}