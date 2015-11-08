package crawler

import org.scalatest._
import org.scalatest.concurrent._
import org.scalatestplus.play._

import scala.concurrent.ExecutionContext.Implicits.global

import java.net.URL

import crawler.Amazon._

class AmazonSpec extends PlaySpec with TryValues {

  "Amazon Crawler" must {

    "return the description of a product given a valid url for a product" in {
      val snippet = "The Sound Bar features DTS TruVolume, which minimizes the distractions of fluctuating volume"
      (new URL("http://www.amazon.com/gp/product/B00SMBFZNG")).productDescription().success.value.map(_.description) getOrElse "n/a" contains snippet mustBe true
    }

    "return nothing if the url was not a product url" in {
      (new URL("http://www.google.com")).productDescription().success.value mustBe empty
    }

    "return failure if the url is invalid" in {
      (new URL("http://" + java.util.UUID.randomUUID.toString)).productDescription().isFailure mustBe true
    }

  }
}