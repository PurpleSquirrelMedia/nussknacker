package pl.touk.nussknacker.engine.kafka

import pl.touk.nussknacker.engine.util.cache.CacheConfig

import scala.concurrent.duration.{DurationInt, FiniteDuration}

case class SchemaRegistryCacheConfig(availableSchemasExpirationTime: Option[FiniteDuration] = Some(1.minute),
                                     parsedSchemaAccessExpirationTime: Option[FiniteDuration] = Some(120.minutes),
                                     maximumSize: Long = CacheConfig.defaultMaximumSize)

object SchemaRegistryCacheConfig {

  val noExpire: SchemaRegistryCacheConfig = SchemaRegistryCacheConfig(None, None)

}