package co.ledger.wallet.daemon.services

import javax.inject.{Inject, Singleton}

import co.ledger.core.{Account, BitcoinLikeNextAccountInfo}
import co.ledger.wallet.daemon.database.{DaemonCache, UserDto}
import co.ledger.wallet.daemon.models
import co.ledger.wallet.daemon.models.AccountDerivationView

import scala.concurrent.Future

@Singleton
class AccountsService @Inject()(defaultDaemonCache: DaemonCache) extends DaemonService {
  def accounts(user: UserDto, poolName: String, walletName: String): Future[Seq[models.AccountView]] = {
    info(LogMsgMaker.newInstance("Obtain accounts with params")
      .append("pool_name", poolName)
      .append("wallet_name", walletName)
      .append("user_pub_key", user.pubKey)
      .toString())
    defaultDaemonCache.getAccounts(user.pubKey, poolName, walletName)
  }

  def account(accountIndex: Int, user: UserDto, poolName: String, walletName: String): Future[models.AccountView] = {
    info(LogMsgMaker.newInstance("Obtain account with params")
      .append("account_index", accountIndex)
      .append("pool_name", poolName)
      .append("wallet_name", walletName)
      .append("user_pub_key", user.pubKey)
      .toString())
    defaultDaemonCache.getAccount(accountIndex, user.pubKey, poolName, walletName)
  }

  def nextAccountCreationInfo(user: UserDto, poolName: String, walletName: String, accountIndex: Option[Int]): Future[AccountDerivationView] = {
    info(LogMsgMaker.newInstance("Obtain next available account creation information")
      .append("account_index", accountIndex)
      .append("pool_name", poolName)
      .append("wallet_name", walletName)
      .append("user_pub_key", user.pubKey)
      .toString())
    defaultDaemonCache.getNextAccountCreationInfo(user.pubKey, poolName, walletName, accountIndex)
  }

  def accountOperation(user: UserDto, accountIndex: Int, cursor: Option[String], batch: Int, fullOp: Int, poolName: String, walletName: String) = {
    info(LogMsgMaker.newInstance("Obtain account operations with params")
      .append("account_index", accountIndex)
      .append("cursor", cursor)
      .append("batch", batch)
      .append("full_op", fullOp)
      .append("wallet_name", walletName)
      .append("pool_name", poolName)
      .toString())

  }

  def createAccount(accountCreationBody: AccountDerivationView, user: UserDto, poolName: String, walletName: String): Future[models.AccountView] = {
    info(LogMsgMaker.newInstance("Create account with params")
      .append("account_derivations", accountCreationBody)
      .append("pool_name", poolName)
      .append("wallet_name", walletName)
      .append("user_pub_key", user.pubKey)
      .toString())
    defaultDaemonCache.createAccount(accountCreationBody, user, poolName, walletName)
  }

}
