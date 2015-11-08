package crawler

import scala.collection.JavaConversions._
import scala.concurrent._
import scala.util.Try

import java.net.URL

import net.ruippeixotog.scalascraper.browser.Browser
import net.ruippeixotog.scalascraper.dsl.DSL._
import net.ruippeixotog.scalascraper.dsl.DSL.Extract._

import models._

import play.api._
import play.api.Play.current

/**
 * This crawler/scraper will scrape Amazon's product page for its description
 */
object Amazon {

  implicit class AmazonURL(url: URL) {

    private[this] lazy val browser = new Browser

    def productDescription(): Try[Option[ProductDescription]] = Try {
      Logger info s"Get Product Description - URL: $url"

      val doc = browser.get(url.toString)

      val results = doc >?> element("#productDescription p").map { e =>
        ProductDescription(e.text)
      }

      Logger info s"URL: $url - Product Description: $results"

      results
    }
  }

}