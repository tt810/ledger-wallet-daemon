package co.ledger.wallet.daemon

import com.google.inject.Provides
import com.jakehschwartz.finatra.swagger.SwaggerModule
import io.swagger.models.auth.BasicAuthDefinition
import io.swagger.models.{Info, Swagger}
import javax.inject.Singleton

object ServerSwaggerModule extends SwaggerModule {

  @Singleton
  def swagger: Swagger = {
    val swagger = new Swagger()

    val info = new Info()
      .description("The Ledger Wallet Daemon allows users to manage cryptocurrency wallets remotely.")
      .version("1.0.0")
      .title("Ledger Wallet Daemon")
    swagger
      .info(info)
        .addSecurityDefinition("DemoAuth", {
          val d = new BasicAuthDefinition()
          d.setType("basic")
          d
        })
    swagger
  }
}