package co.ledger.wallet.daemon.converters

import co.ledger.core.Wallet
import co.ledger.wallet.daemon.models
import com.twitter.util.Future

class WalletConverter {

  def apply(wallet: Wallet): Future[models.Wallet] = ???

}
