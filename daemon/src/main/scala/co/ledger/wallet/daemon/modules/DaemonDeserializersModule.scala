package co.ledger.wallet.daemon.modules

import com.fasterxml.jackson.databind.module.SimpleModule
import co.ledger.wallet.daemon.models.{NetworkParams, NetworkParamsDeserializer}

class DaemonDeserializersModule extends SimpleModule {
  addDeserializer(classOf[NetworkParams], new NetworkParamsDeserializer)
}

