package co.ledger.wallet.daemon.services

import javax.inject.{Inject, Singleton}

import co.ledger.core.Account
import co.ledger.wallet.daemon.database.User
import co.ledger.core.implicits._

import scala.collection.JavaConverters._

@Singleton
class AccountsService @Inject()(walletsService: WalletsService) {
  import AccountsService._

  def accounts(user: User, poolName: String, walletName: String, offset: Int, bulkSize: Int) =
    walletsService.wallet(user, poolName, walletName) flatMap {(wallet) =>
      wallet.getAccountCount() flatMap {(count) =>
        wallet.getAccounts(offset, bulkSize) map {(accounts) =>
          AccountBulk(count, offset, bulkSize, accounts.asScala.toArray)
        }
      }
    }

  def account(user: User, poolName: String, walletName: String, accountIndex: Int) =
    walletsService.wallet(user, poolName, walletName).flatMap(_.getAccount(accountIndex))

//  def removeAccount(user: User, poolName: String, walletName: String, accountIndex: Int) = TODO implement once exists on the lib
//    walletsService.wallet(user, poolName, walletName) flatMap {(wallet) =>
//      wallet.
//    }

}

object AccountsService {
  case class AccountBulk(count: Int, offset: Int, bulkSize: Int, accounts: Array[Account])
}