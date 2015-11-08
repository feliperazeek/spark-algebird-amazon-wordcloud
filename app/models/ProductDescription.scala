package models

import play.api.libs.json._

/**
 * This is what function productDescription() on crawlers return
 */
case class ProductDescription(
  description: String)

object ProductDescription {
  implicit val format: Format[ProductDescription] = Json.format[ProductDescription]
}
