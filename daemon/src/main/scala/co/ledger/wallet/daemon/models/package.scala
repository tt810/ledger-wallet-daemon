package co.ledger.wallet.daemon

import io.swagger.annotations.ApiModelProperty

package object models {

  case class Pool(
                   @ApiModelProperty(value = "Name of the pool") name: String,
                   @ApiModelProperty(value = "The number of wallet managed by the pool") wallet_count: Int
                 )

  case class BitcoinLikeNetworkParams(

                                )

  case class Currency(
                      bitcoin_like_network_params: Option[BitcoinLikeNetworkParams]
                     )

  case class Wallet(
                    name: String,
                    currency: Currency
                   )

}
