package co.ledger.wallet.daemon

import com.fasterxml.jackson.annotation.JsonProperty

case class ErrorResponseBody(@JsonProperty("type") rc: ErrorCode, @JsonProperty("message") msg: String)
