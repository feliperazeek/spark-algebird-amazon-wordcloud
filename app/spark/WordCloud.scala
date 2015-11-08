package spark

import akka.actor._

import scala.concurrent.duration._
import scala.collection.JavaConverters._
import scala.util.{ Try, Success, Failure }

import com.twitter.algebird._
import com.twitter.algebird.mutable._
import com.twitter.algebird.CMSHasherImplicits._

import com.twitter.bijection.{ Bijection, Injection }

import com.twitter.storehaus._
import com.twitter.storehaus.redis._

import com.twitter.finagle.redis._
import com.twitter.finagle.redis.util._
import com.twitter.finagle.builder._

import com.twitter.util._

import java.net.URL
import java.util._

import play.api.Logger

import org.jboss.netty.buffer.ChannelBuffer

import org.apache.spark.SparkConf
import org.apache.spark.SparkContext._
import org.apache.spark.storage.StorageLevel
import org.apache.spark.streaming.{ Seconds, StreamingContext }
import org.apache.spark.streaming.dstream._
import org.apache.spark.streaming.StreamingContext._

import common._
import common.nlp._
import actor._
import models._
import crawler.Amazon._
import spark.SparkConfig._

/**
 * This spark streaming job will receive Amazon product urls from an actor mailbox,
 * will get their product descriptions, tokenize them and store top-k in Redis
 */
object WordCloud {

  lazy val RedisHost = config.string("redis.host").mandatory + ":" + config.string("redis.port").mandatory
  lazy val RedisKey = StringToChannelBuffer(config.string("redis.key").mandatory)

  lazy val TopK = config.int("wordcloud.count") or 25
  lazy val Capacity = config.int("wordcloud.capacity") or 1000

  private[this] val CheckpointPath = "./wordcloud-checkpoint"

  lazy val ssc = Try(StreamingContext.getOrCreate(CheckpointPath, newContext)) match {
    case Success(ctx) =>
      ctx
    case Failure(error) =>
      sys.error(s"Error starting Spark Streaming context. Please delete checkpoint directory: ${CheckpointPath} (rm -rf target/universal/stage/wordcloud-checkpoint/).")
  }

  // Storehaus Bijection
  implicit def stringToChannelBuffer: Bijection[String, ChannelBuffer] = Bijection.build(StringToChannelBuffer(_: String))(CBToString(_: ChannelBuffer))

  def apply() {
    Logger info s"Starting Spark Job: WordCloud"

    ssc.start()
    ssc.awaitTermination()
  }

  def newContext() = {
    // Start new Spark streaming context with checkpointing because we want the job to be stateful
    val ssc = new StreamingContext(sparkConf, Seconds(5))
    ssc.checkpoint(CheckpointPath)

    implicit val monoid = TopNCMS.monoid[String](eps = 0.01, delta = 1E-3, seed = 1, heavyHittersN = Capacity)

    def accumulate(values: Seq[TopCMS[String]], state: Option[TopCMS[String]])(implicit m: TopNCMSMonoid[String]) =
      m.sumOption(state.toSeq ++ values)

    // Ideally this would be a reliable receiver such as Kafka using Spark's direct api
    val stream = ssc.actorStream[String](Props[RouterActor], "WordCloud", storageLevel = StorageLevel.MEMORY_AND_DISK_SER)

    // Extract - Get words from urls stream (http request, scrape, tokenize, filter stop words, stem)
    val tokens = stream
      .flatMap { url =>
        val description = (new URL(url)).productDescription().toOption.flatten
        Logger info s"Product Url: ${url} - Description: $description"
        description
      }
      .flatMap { d => d.description.words() }
      .map { s => s -> monoid.create(s) }

    // Tranform
    val state = tokens
      .updateStateByKey(accumulate _) // I could potentially use dstream.window() if we wanted to do it over a specific time period
      .map(_._2)
      .reduce { (a, b) => monoid.plus(a, b) }

    // Load
    state.foreachRDD { rdd =>
      if (rdd.count() != 0) {
        val m = rdd.first()

        val topK = m
          .heavyHitters
          .map { id =>
            (id, m.frequency(id).estimate)
          }
          .toSeq
          .sortBy(_._2)
          .reverse
          .slice(0, TopK)
          .map { case (word, count) => (Injection[String, ChannelBuffer](word) -> count.toDouble) }

        val store = RedisSortedSetStore(RedisClient())
        store.put((RedisKey, Option(topK)))
      }
    }

    ssc
  }

  Try {
    sys.ShutdownHookThread {
      Logger.info("Gracefully stopping streaming context, takes just a couple of seconds...")

      // Doing a sleep to try to avoid this bug: https://issues.apache.org/jira/browse/SPARK-11104
      Thread sleep 2000

      ssc.stop(stopSparkContext = true, stopGracefully = true)
      ssc.awaitTermination()
      Logger.info("Done!")
    }
  }
}