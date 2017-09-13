package co.ledger.wallet.daemon

package object exceptions {

  class ResourceNotFoundException(message: String) extends Exception(message)

  class OtherCoreException(message: String) extends Exception(message)
}
