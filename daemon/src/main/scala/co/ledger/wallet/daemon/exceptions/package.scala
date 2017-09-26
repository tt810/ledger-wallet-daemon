package co.ledger.wallet.daemon

package object exceptions {

  case class WalletNotFoundException(        walletName: String) extends DaemonException(s"Wallet $walletName doesn't exist")
  case class WalletAlreadyExistException(    walletName: String) extends DaemonException(s"Wallet $walletName already exists")
  case class WalletPoolNotFoundException(      poolName: String) extends DaemonException(s"Wallet pool $poolName doesn't exist")
  case class WalletPoolAlreadyExistException(  poolName: String) extends DaemonException(s"Wallet pool $poolName already exists")
  case class CurrencyNotFoundException(    currencyName: String) extends DaemonException(s"Currency $currencyName is not supported")
  case class CurrencyAlreadyExistException(currencyName: String) extends DaemonException(s"Currency $currencyName already exists")
  case class UserNotFoundException(              pubKey: String) extends DaemonException(s"User $pubKey doesn't exist")
  case class UserAlreadyExistException(          pubKey: String) extends DaemonException(s"User $pubKey already exists")

  case class DaemonDatabaseException(msg: String, e: Throwable)  extends Exception(msg, e)
  class DaemonException(msg: String, e: Throwable = null)        extends Exception(msg, e)
}
