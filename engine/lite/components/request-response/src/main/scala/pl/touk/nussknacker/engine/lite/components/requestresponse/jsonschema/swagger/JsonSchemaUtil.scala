package pl.touk.nussknacker.engine.lite.components.requestresponse.jsonschema.swagger

import org.everit.json.schema.Schema
import org.everit.json.schema.loader.SchemaLoader
import org.json.JSONObject

// TODO: Move it to NU, create extra module: json-util? Similar to avro-util
object JsonSchemaUtil {

  def parseSchema(rawJsonSchema: String): Schema =
    parseSchema(rawJsonSchema, useDefaults = true, nullableSupport = true)

  def parseSchema(rawJsonSchema: String, useDefaults: Boolean, nullableSupport: Boolean): Schema = {
    val rawSchema: JSONObject = new JSONObject(rawJsonSchema)

    SchemaLoader
      .builder()
      .useDefaults(useDefaults)
      .nullableSupport(nullableSupport)
      .schemaJson(rawSchema)
      .draftV7Support()
      .build()
      .load()
      .build()
      .asInstanceOf[Schema]
  }

}