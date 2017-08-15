package co.ledger.wallet.cli.commands.wallet

import co.ledger.wallet.cli.Client
import co.ledger.wallet.cli.commands.CliCommand
import de.vandermeer.asciitable.AsciiTable
import org.backuity.clist.{Command, arg}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

object WalletListCommand extends Command(name = "wallet/list", description = "Lists all wallet from the given pool") with CliCommand {
  var pool_name = arg[String](description = "The name of the pool")

  override def run(client: Client): Future[Unit] = {
    client.api.wallet.listWallets(pool_name).map(protocolize).map {(wallets) =>
      val table = new AsciiTable()
      table.addRule()
      table.addRow("Name", "Currency", "Number of accounts", "Synchronizing")
      table.addRule()
      for (wallet <- wallets) {
        table.addRow(wallet.name, wallet.currency, new Integer(wallet.accountNumber), if (wallet.synchronizing) "yes" else "no")
        table.addRule()
      }
      print(table.render())
      ()
    }
  }
}
