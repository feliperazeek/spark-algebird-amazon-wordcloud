package controllers

import org.scalatest._
import org.scalatestplus.play._

import play.api.test._
import play.api.test.Helpers._
import play.api.libs.ws._

import scala.concurrent.ExecutionContext.Implicits.global

class ApplicationSpec extends PlaySpec with TryValues with OneAppPerSuite {
  "run in a server" in {
    running(TestServer(3333)) {

      await(WS.url("http://localhost:3333").get).status mustBe (OK)

    }
  }
}