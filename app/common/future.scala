package common

import scala.concurrent._
import com.twitter.util.{ Future ⇒ TwitterFuture, Return, Throw }

/**
 * Future-related helpers especially conversions such as Twitter <-> Akka
 */
package object future {

  implicit def fromTwitter[A](twitterFuture: TwitterFuture[A]): Future[A] = {
    val promise = Promise[A]()
    twitterFuture respond {
      case Return(a) => promise success a
      case Throw(e) => promise failure e
    }
    promise.future
  }

}