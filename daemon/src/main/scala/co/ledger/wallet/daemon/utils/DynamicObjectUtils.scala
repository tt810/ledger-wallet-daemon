package co.ledger.wallet.daemon.utils

import co.ledger.core.{DynamicArray, DynamicObject}
import upickle.Js
import upickle.json._

object DynamicObjectUtils {

  def fromJSON(rawJson: String): DynamicObject = {
    val json = read(rawJson)
    val result = DynamicObject.newInstance()
    convertObject(result, json.obj)
    result
  }

  private def convertObject(out: DynamicObject, in: Map[String, Js.Value]): Unit = {
    for ((key, value) <- in) {
      value.value match {
        case double: Double =>
          out.putDouble(key, double)
        case string: String =>
          out.putString(key, string)
        case array: Seq[Js.Value] @unchecked =>
          val arr = DynamicArray.newInstance()
          convertArray(arr, array)
          out.putArray(key, arr)
        case obj: Map[String, Js.Value] @unchecked =>
          val o = DynamicObject.newInstance()
          convertObject(o, obj)
          out.putObject(key, o)
      }
    }
  }

  private def convertArray(out: DynamicArray, in: Seq[Js.Value]): Unit = {
    for (value <- in) {
      value.value match {
        case double: Double =>
          out.pushDouble(double)
        case string: String =>
          out.pushString(string)
        case array: Seq[Js.Value] @unchecked =>
          val arr = DynamicArray.newInstance()
          convertArray(arr, array)
          out.pushArray(arr)
        case obj: Map[String, Js.Value] @unchecked =>
          val o = DynamicObject.newInstance()
          convertObject(o, obj)
          out.pushObject(o)
      }
    }
  }

}
