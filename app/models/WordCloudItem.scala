package models

import play.api.libs.json._

/**
 * Outpput of the REST API endpoint for a Word Cloud (list of items to be precise)
 */
case class WordCloudItem(
  text: String,
  size: Int)

object WordCloudItem {
  implicit val format: Format[WordCloudItem] = Json.format[WordCloudItem]
}
