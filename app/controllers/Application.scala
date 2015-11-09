package controllers

import akka.actor._

import play.api._
import play.api.mvc._
import play.api.mvc.BodyParsers._
import play.api.libs.json._
import play.api.libs.concurrent.Execution.Implicits.defaultContext

import scala.util.control.NonFatal
import scala.concurrent._
import scala.collection.JavaConversions._

import com.twitter.bijection.{ Bijection, Injection }

import com.twitter.util.{ Future ⇒ TwitterFuture }

import com.twitter.storehaus._
import com.twitter.storehaus.redis._

import com.twitter.finagle.redis._
import com.twitter.finagle.redis.util._
import com.twitter.finagle.builder._

import org.jboss.netty.buffer.ChannelBuffer

import models._
import common.future._
import actor._
import common._
import common.eventbus._
import crawler.Amazon._
import spark.SparkConfig._
import models.ProductDescription._

import java.net.URL
import java.security.MessageDigest

class Application extends Controller {

  implicit def stringToChannelBuffer: Bijection[String, ChannelBuffer] = Bijection.build(StringToChannelBuffer(_: String))(CBToString(_: ChannelBuffer))

  lazy val RedisKey = StringToChannelBuffer(config.string("redis.key").mandatory)
  lazy val CheckDuplicates = config.boolean("check.duplicates").mandatory

  /**
   * Simple visualization for Word Cloud
   */
  def index = Action.async {
    Future {
      Ok(views.html.index(refresh = 5))
    }
  }

  /**
   * Json representation of Word Cloud
   */
  def data = Action.async {
    lazy val store = RedisSortedSetStore(RedisClient())

    // In a real application we could probably wrap this using Akka's CircuitBreaker to fail fast in case Redis is down
    store.get(RedisKey).map { maybe =>
      maybe match {
        case Some(list) =>
          val json = JsArray(list.map { i =>
            Json toJson WordCloudItem(Injection[ChannelBuffer, String](i._1), i._2.toInt)
          })

          Ok(json)

        case _ =>
          NotFound("""No words are available! To submit an url: curl -i "http://localhost:9000/submit?url=http%3A%2F%2Fwww.amazon.com%2Fgp%2Fproduct%2FB00SMBFZNG" """)
      }

    } recover {
      case NonFatal(error) =>
        InternalServerError(error.getMessage)
    }
  }

  /**
   * Endpoint that accepts url of an Amazon product to be processed (fire & forgets to actor that route message to Spark)
   * It checks for duplication using Redis Bloom Filter
   */
  def submit(url: String) = Action.async {
    val key = StringToChannelBuffer(url)
    val client = RedisClient()

    // We might wanna catch a possible exception here and let the request go through in case Redis is down for example
    val bitFuture: TwitterFuture[java.lang.Long] = if (CheckDuplicates) client.getBit(key, offset = 0)
    else TwitterFuture value 0l

    // Using Redis Bloom Filter because I want to return the duplication error to the user
    // and also avoid any overhead of any other component of the system if this was a real life
    bitFuture.map { bit =>
      bit match {
        case x if x == 0l =>
          Logger info s"Submit URL: $url"

          // In a real application this probably should produce to a Kafka topic
          implicit val <> = actorSystem
          mailman deliver new URL(url)

          // Mark as read on Redis-backed Bloom Filter
          // For the sake of simplicity it works being here, on a real application we would probably
          // need something more sophisticated which guaranteed that the URL got processed
          // correctly before being added to the bloom filter.
          if (CheckDuplicates) client.setBit(key, offset = 0, value = 1)

          Ok("The url has been submitted successfully!")

        case _ =>
          val msg = s"URL ($url) has most likely been submitted already (bit: $bit)!"
          Logger info msg

          BadRequest(msg)
      }
    }
  }

}
