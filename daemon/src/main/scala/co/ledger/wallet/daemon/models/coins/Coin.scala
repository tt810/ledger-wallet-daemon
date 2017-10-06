package co.ledger.wallet.daemon.models.coins

import co.ledger.core
import co.ledger.wallet.daemon.models.CurrencyFamily


trait Coin {

  val currencyFamily: CurrencyFamily

  def getNetworkParamsView(from: core.Currency): NetworkParamsView

//  def transactionView: TransactionView


//  def getBlock
//
//  def getTransaction
//
//  def getInput
//
//  def getOutput


}

trait NetworkParamsView

trait TransactionView

trait BlockView

trait InputView

trait OutputView


