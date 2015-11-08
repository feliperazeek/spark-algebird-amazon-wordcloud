package spark

import akka.actor._

import com.twitter.algebird._
import com.twitter.algebird.CMSHasherImplicits._

import scala.concurrent._

import org.apache.spark._
import org.apache.spark.streaming._
import org.apache.spark.streaming.receiver._
import org.apache.spark.SparkContext._
import org.apache.spark.storage.StorageLevel

import play.api.Play.current
import play.api.Logger
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import play.api.libs.concurrent.Akka

import common._
import actor._

/**
 * In a real application we would be using spark-submit to submit Spark jobs instead of starting them from a Play application
 * This object only exists for the purpose of the simplicity of this demo
 */
object SparkConfig {

  val sparkConf = new SparkConf()
    .setMaster(config.string("spark.master-uri").mandatory)
    .setAppName(config.string("spark.app-name").mandatory)
    .set("spark.driver.port", config.string("spark.driver.port").mandatory)
    .set("spark.driver.host", config.string("spark.driver.host").mandatory)
    .set("spark.logConf", config.string("spark.logConf") or "true")
    .set("spark.serializer", "org.apache.spark.serializer.KryoSerializer")
    .set("spark.kryo.registrator", "spark.Kryo")

  // FIXME We shoudn't be accessing SparkEnv directly, this is just a demo. We would be using spark-submit to submit jobs in real life
  lazy val actorSystem = {
    val sparkEnv = retry(50)(() => SparkEnv.get)
    Logger info s"Spark Env: $sparkEnv"

    sparkEnv.actorSystem
  }

  // This actor will be listening for urls being published to Akka's event bus and will route them to Spark
  lazy val routerActor = actorSystem.actorOf(Props[RouterActor], "Router")

  def init() = {
    // Start WordCloud Spark Job (just a demo, would be using spark-submit in real life)
    val wordCloud = new Thread(new Runnable {
      def run() = WordCloud()
    })

    wordCloud.start
  }

  def stop() = {
    Logger info s"Sending poison pill to Spark router actor..."
    routerActor ! PoisonPill
  }

  @scala.annotation.tailrec
  def retry[T](n: Int)(fn: () => T): T = {
    fn() match {
      case x if x != null => x
      case _ if n > 1 =>
        Thread sleep 1000
        retry(n - 1)(fn)
      case _ => sys.error(s"I am done trying!")
    }
  }

}