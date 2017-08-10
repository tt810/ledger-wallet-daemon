package co.ledger.wallet.cli.commands

import co.ledger.wallet.cli.Client
import de.vandermeer.asciitable.AsciiTable
import org.backuity.clist.{Command, arg, opt}

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

object OpenPoolCommand extends Command(name = "pool/run", description = "Starts monitoring the given pool") with CliCommand {

  var pool_name = arg[String](description = "The name of the pool to open")
  var password = opt[Option[String]](description = "Password to provide if the pool is encrypted")

  override def run(client: Client): Future[Unit] = {
    client.api.pool.openPool(pool_name, password.orNull) map protocolize
  }
}
