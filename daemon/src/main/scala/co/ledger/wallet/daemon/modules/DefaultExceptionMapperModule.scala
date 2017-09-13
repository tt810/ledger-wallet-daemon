package co.ledger.wallet.daemon.modules

import co.ledger.wallet.daemon.mappers._
import com.twitter.finatra.http.exceptions.ExceptionManager
import com.twitter.inject.{Injector, TwitterModule}

object DefaultExceptionMapperModule extends TwitterModule {

  override def singletonStartup(injector: Injector): Unit = {
    val manager = injector.instance[ExceptionManager]
    manager.add[ResourceNotFoundExceptionMapper]
    manager.add[OtherCoreExceptionMapper]
  }
}
