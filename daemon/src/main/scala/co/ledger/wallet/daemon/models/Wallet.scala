package co.ledger.wallet.daemon.models

import co.ledger.core
import co.ledger.wallet.daemon.utils
import com.fasterxml.jackson.annotation.JsonProperty
import co.ledger.core.implicits._
import scala.collection.JavaConverters._
import scala.concurrent.{ExecutionContext, Future}

object Wallet {

  def newView(coreWallet: core.Wallet)(implicit ec: ExecutionContext): Future[WalletView] = {

    def getBalance(count: Int): Future[Long] = {
      coreWallet.getAccounts(0, count) flatMap { (accounts) =>
        val accs = accounts.asScala.toList
        val balances = Future.sequence(for (acc <- accs) yield acc.getBalance())
        balances.map { bs =>
          val bls = for(balance <- bs) yield balance.toLong
          utils.sum(bls)
        }
      }
    }

    val configuration: Map[String, Any] = {
      var configs = collection.mutable.Map[String, Any]()
      val dynamicObject = core.DynamicObject.newInstance()
      dynamicObject.getKeys.forEach { key =>
        configs += (key -> dynamicObject.getObject(key))
      }
      configs.toMap
    }

    coreWallet.getAccountCount().flatMap { count =>
      getBalance(count).map { balance =>
        WalletView(coreWallet.getName, count, balance, Currency.newView(coreWallet.getCurrency), configuration)
      }
    }
  }

}

case class WalletView(
                       @JsonProperty("name") name: String,
                       @JsonProperty("account_count") accountCount: Int,
                       @JsonProperty("balance") balance: Long,
                       @JsonProperty("currency") currency: CurrencyView,
                       @JsonProperty("configuration") configuration: Map[String, Any]
                     )

case class WalletsViewWithCount(count: Int, wallets: Seq[WalletView])