package actor

import akka.actor._
import akka.event._
import akka.pattern.pipe

import akka.actor.OneForOneStrategy
import akka.actor.SupervisorStrategy._

import play.api.libs.concurrent.Execution.Implicits.defaultContext

import java.net.URL

import crawler._

import org.apache.spark._
import org.apache.spark.streaming._
import org.apache.spark.streaming.receiver._

import scala.concurrent._
import scala.concurrent.duration._

import scala.util.control.NonFatal

import models._
import common._
import common.eventbus._

/**
 * This actor will just forward messages to Spark to be processed by streaming jobs
 */
class RouterActor extends Actor with ActorHelper {

  override def preStart = {
    mailman subscribe self to Seq(
      classOf[java.net.URL]
    )
  }

  override val supervisorStrategy =
    OneForOneStrategy(maxNrOfRetries = 10, withinTimeRange = 1 minute) {
      case NonFatal(_) => Restart
    }

  def receive = {
    case url: java.net.URL =>
      log info s"Word Cloud - URL: ${url}"
      store(url.toString)

    case other =>
      log warn s"Received unknown message: $other"
  }
}
