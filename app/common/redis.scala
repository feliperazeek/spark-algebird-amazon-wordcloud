package common

import com.twitter.finagle.redis._
import com.twitter.finagle.redis.util._
import com.twitter.finagle.builder._

object RedisClient {

  lazy val RedisHost = config.string("redis.host").mandatory + ":" + config.string("redis.port").mandatory

  def apply() = new Client(
    ClientBuilder()
      .hosts(RedisHost)
      .hostConnectionLimit(1)
      .codec(Redis())
      .daemon(true)
      .build()
  )

}