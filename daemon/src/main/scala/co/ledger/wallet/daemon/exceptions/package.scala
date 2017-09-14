package co.ledger.wallet.daemon

import scala.reflect.ClassTag

package object exceptions {

  case class ResourceNotFoundException[T: ClassTag](resourceType: T, resourceName: String)
    extends Exception(s"$resourceType $resourceName doesn't exist")

  case class ResourceAlreadyExistException[T: ClassTag](resourceType: T, resourceName: String)
    extends Exception(s"$resourceType $resourceName already exists")
}
