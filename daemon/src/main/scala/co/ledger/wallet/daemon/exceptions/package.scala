package co.ledger.wallet.daemon

import scala.reflect.ClassTag

package object exceptions {

  case class ResourceNotFoundException[T: ClassTag](resourceType: T, resourceName: String)
    extends DaemonException(s"$resourceType $resourceName doesn't exist")

  case class ResourceAlreadyExistException[T: ClassTag](resourceType: T, resourceName: String, e: Exception = null)
    extends DaemonException(s"$resourceType $resourceName already exists", e)

  case class DaemonDatabaseException(msg: String, e: Throwable) extends Exception(msg, e)

  class DaemonException(msg: String, e: Exception = null) extends Exception(msg, e)
}
