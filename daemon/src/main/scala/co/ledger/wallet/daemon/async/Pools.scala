package co.ledger.wallet.daemon.async

import java.util.concurrent.ThreadFactory

import com.google.common.util.concurrent.ThreadFactoryBuilder

object Pools {
  def newNamedThreadFactory(prefix: String): ThreadFactory = {
    new ThreadFactoryBuilder()
      .setNameFormat(s"$prefix-%d")
      .build()
  }
}
