package common

import com.typesafe.config.{ Config, ConfigException, ConfigFactory }
import play.api.Play
import scalaz.Scalaz._
import scala.util.Try

/**
 * Usage:
 * - (config string "database.default.driver").mandatory
 * - (config string "database.enabled").or(true)
 * - config("pricing.conf") string "price.default"
 */
sealed trait Configurator {
  val name: String

  def getString(path: String): Option[String]

  def string(path: String): ConfigOption[String] = ConfigOption(path, getString(path))

  def boolean(path: String): ConfigOption[Boolean] = string(path) map (_ === true.toString)

  def int(path: String): ConfigOption[Int] = string(path) flatMap (s => Try(s.toInt).toOption)

}

sealed case class ConfigOption[T](path: String, opt: Option[T]) {
  def mandatory: T = opt match {
    case Some(v) => v
    case _ => throw new ConfigException.Missing(path)
  }

  def or(default: T): T = opt getOrElse default

  def map[T2](f: T => T2) = ConfigOption[T2](path, opt map f)

  // Yes I know, not a true flatMap
  def flatMap[T2](f: T => Option[T2]) = ConfigOption[T2](path, opt flatMap f)
}

// DefaultConfigurator uses Play application.conf config and all included stuff
sealed class DefaultConfigurator extends Configurator {
  override val name: String = "application.conf"

  override def getString(path: String): Option[String] = Play.current.configuration.getString(path)
}

object config extends DefaultConfigurator {
  def apply(confFile: String) = {
    ConfigFactory.load(confFile)
  }
}
