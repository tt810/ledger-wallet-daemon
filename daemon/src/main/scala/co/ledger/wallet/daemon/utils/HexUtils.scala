package co.ledger.wallet.daemon.utils

object HexUtils {
  def valueOf(array: Array[Byte]): String = array.map("%02X" format _).mkString
  def valueOf(string: String): Array[Byte] =  string.sliding(2, 2).toArray.map(Integer.parseInt(_, 16).toByte)
}
