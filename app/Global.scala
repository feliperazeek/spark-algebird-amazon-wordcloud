import play.api._

object Global extends GlobalSettings {

  override def onStart(app: Application) = {
    spark.SparkConfig.init()
  }

}