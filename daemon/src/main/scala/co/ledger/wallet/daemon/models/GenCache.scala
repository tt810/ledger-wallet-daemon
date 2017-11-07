package co.ledger.wallet.daemon.models

import java.util.concurrent.ConcurrentHashMap

import scala.collection._
import scala.collection.JavaConverters._

trait GenCache {

  type Cache[K, V] = concurrent.Map[K, V]

  def newCache[K, V](initialCapacity: Int): Cache[K, V] = new ConcurrentHashMap[K, V](initialCapacity).asScala

}
