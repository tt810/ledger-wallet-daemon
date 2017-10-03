package co.ledger.wallet.daemon.modules

import com.fasterxml.jackson.databind.module.SimpleModule
import co.ledger.wallet.daemon.models.{BitcoinLikeNetworkParams, Currency, CurrencyFamily, Wallet, CurrencyUnit => ModelUnit}
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.{DeserializationContext, JsonDeserializer, JsonNode, ObjectMapper}
import com.fasterxml.jackson.module.scala.DefaultScalaModule
import com.fasterxml.jackson.module.scala.experimental.ScalaObjectMapper

import scala.collection.JavaConverters._

class DaemonDeserializersModule extends SimpleModule {
  addDeserializer(classOf[Currency], new Deserializers.CurrencyDeserializer)
  addDeserializer(classOf[Wallet], new Deserializers.WalletDeserializer)
}

object Deserializers {

  class WalletDeserializer extends JsonDeserializer[Wallet] {

    override def deserialize(jp: JsonParser, ctxt: DeserializationContext): Wallet = {
      val node: JsonNode = jp.getCodec.readTree(jp)
      val name = node.get("name").asText()
      val accountCount = node.get("account_count").asInt()
      val balance = node.get("balance").asLong()
      val configuration = mapper.readValue[Map[String, Any]](node.get("configuration").toString, classOf[Map[String, Any]])
      val currency = mapper.readValue[Currency](node.get("currency").toString, classOf[Currency])
      Wallet(name, accountCount, balance, currency, configuration)
    }

    private val mapper: ObjectMapper = new ObjectMapper() with ScalaObjectMapper
    private val module = new SimpleModule()
    module.addDeserializer(classOf[Currency], new CurrencyDeserializer)
    mapper.registerModule(module)
    mapper.registerModule(DefaultScalaModule)
  }

  class CurrencyDeserializer extends JsonDeserializer[Currency] {

    override def deserialize(jp: JsonParser, ctxt: DeserializationContext): Currency = {
      val node: JsonNode = jp.getCodec.readTree(jp)
      val name = node.get("name").asText()
      val family = CurrencyFamily.valueOf(node.get("family").asText())
      val bip44CoinType = node.get("bip_44_coin_type").asInt()
      val paymentUriScheme = node.get("payment_uri_scheme").asText()
      val unitsIter = node.path("units").iterator().asScala
      val units = for (unit <- unitsIter) yield mapper.readValue[ModelUnit](unit.toString(), classOf[ModelUnit])
      val networkParams = family match {
        case CurrencyFamily.BITCOIN => mapper.readValue[BitcoinLikeNetworkParams](node.get("network_params").toString(), classOf[BitcoinLikeNetworkParams])
        case _ => throw new NotImplementedError()
      }
      Currency(name, family, bip44CoinType, paymentUriScheme, units.toList, networkParams)
    }

    private val mapper: ObjectMapper = new ObjectMapper() with ScalaObjectMapper
    mapper.registerModule(DefaultScalaModule)
  }

}