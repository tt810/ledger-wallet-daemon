package co.ledger.wallet.cli.commands.pool

import co.ledger.wallet.cli.Client
import co.ledger.wallet.cli.commands.CliCommand
import org.backuity.clist.{Command, arg, opt}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

object OpenPoolCommand extends Command(name = "pool/run", description = "Starts monitoring the given pool") with CliCommand {

  var pool_name = arg[String](description = "The name of the pool to open")
  var password = opt[Option[String]](description = "Password to provide if the pool is encrypted")

  override def run(client: Client): Future[Unit] = {
    client.api.pool.openPool(pool_name, password.orNull) map protocolize
  }
}
