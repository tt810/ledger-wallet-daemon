package co.ledger.wallet.cli.commands

import co.ledger.wallet.cli.Client
import org.backuity.clist.Command
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

object LedgerCoreCommand extends Command(name = "lib/version", description = "Fetches the version of the core lib.")
  with CliCommand {
  override def run(client: Client): Future[Unit] = {
    client.api.lib.getLibraryVersion().map(println)
  }
}

