package common

import akka.actor._
import akka.actor.SupervisorStrategy.Restart

import scala.concurrent._
import scala.concurrent.duration._

/**
 * Simple helper to deal with Akka's EventBus
 */
package object eventbus {

  /**
   * Akka Event Bus Subscription
   * example: mailman subscribe actorRef to Seq(classOf[EventType1], classOf[EventType2]])
   */
  class AkkaBusSubscription(ref: ActorRef) {
    def to(eventTypes: Seq[Class[_]])(implicit context: ActorContext) = {
      eventTypes foreach { clazz =>
        context.system.eventStream.subscribe(ref, clazz)
      }
    }
  }

  /**
   * Sugar so you can subscribe to events and send events to Akka's eventbus
   * mailman deliver event
   */
  object mailman {
    def subscribe(actor: ActorRef) = new AkkaBusSubscription(actor)
    def unsubscribe(actor: ActorRef)(implicit context: ActorContext) = context.system.eventStream unsubscribe actor
    def deliver(event: Object)(implicit system: ActorSystem): Unit = system.eventStream publish event
    def publish(event: Object)(implicit context: ActorContext): Unit = deliver(event)(context.system)
  }

}
