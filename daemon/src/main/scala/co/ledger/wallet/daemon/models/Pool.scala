package co.ledger.wallet.daemon.models

import co.ledger.core
import co.ledger.core.implicits._
import com.fasterxml.jackson.annotation.JsonProperty

import scala.concurrent.{ExecutionContext, Future}

object Pool {

  def newView(pool: core.WalletPool)(implicit ec: ExecutionContext): Future[WalletPoolView] =
    pool.getWalletCount().map(WalletPoolView(pool.getName, _))

}

case class WalletPoolView(
                           @JsonProperty("name") name: String,
                           @JsonProperty("wallet_count") walletCount: Int
                         )

