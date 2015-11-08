package spark

import org.apache.spark.serializer.KryoRegistrator

/**
 * See Spark documentation for more information on Spark and Kryo:
 * http://spark.apache.org/docs/latest/tuning.html
 */
class Kryo extends KryoRegistrator {
  override def registerClasses(kryo: com.esotericsoftware.kryo.Kryo) {
    kryo.register(classOf[java.net.URL])
    kryo.register(classOf[models.ProductDescription])
  }
}