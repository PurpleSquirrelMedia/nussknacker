package pl.touk.nussknacker.engine.lite.components.requestresponse.jsonschema.common.sinks.json

import cats.data.NonEmptyList
import org.everit.json.schema.Schema
import pl.touk.nussknacker.engine.api.{LazyParameter, NodeId}
import pl.touk.nussknacker.engine.api.context.transformation.{BaseDefinedParameter, NodeDependencyValue}
import pl.touk.nussknacker.engine.api.context.ValidationContext
import pl.touk.nussknacker.engine.api.definition._
import pl.touk.nussknacker.engine.api.process.Sink
import pl.touk.nussknacker.engine.lite.components.requestresponse.jsonschema.common.sinks.ResponseRequestSinkImplFactory
import pl.touk.nussknacker.engine.lite.components.requestresponse.jsonschema.swagger.{JsonOutputValidator, JsonRequestResponseBaseTransformer}

//TODO: Move it to NU
object JsonRequestResponseSinkFactory {

  final val SinkValueParamName = "Value"

  private val sinkParamsDefinition = List(
    Parameter[AnyRef](SinkValueParamName).copy(isLazyParameter = true),
  )

  case class RequestResponseSinkState(schema: Schema)
}

class JsonRequestResponseSinkFactory(implProvider: ResponseRequestSinkImplFactory) extends JsonRequestResponseBaseTransformer[Sink] {

  import JsonRequestResponseSinkFactory._

  override type State = RequestResponseSinkState

  //FIXME
  val OutputSchemaProperty: String = "outputSchema"

  override def contextTransformation(context: ValidationContext, dependencies: List[NodeDependencyValue])(implicit nodeId: NodeId): NodeTransformationDefinition = {
    case TransformationStep(Nil, _) =>
      NextParameters(parameters = sinkParamsDefinition, errors = Nil)
    case TransformationStep((SinkValueParamName, value: BaseDefinedParameter) :: Nil, _) =>
      val determinedSchema = getRawSchemaFromProperty(OutputSchemaProperty, dependencies)

      val validationResult = determinedSchema
        .andThen{ case (_, schema) =>
          JsonOutputValidator.validateOutput(value.returnType, schema).leftMap(NonEmptyList.one)
        }.swap.toList.flatMap(_.toList)

      val finalState = determinedSchema.toOption.map{
        case (_, schema)  => RequestResponseSinkState(schema)
      }

      FinalResults(context, validationResult, finalState)
  }

  override def implementation(params: Map[String, Any], dependencies: List[NodeDependencyValue], finalStateOpt: Option[State]): Sink = {
    val value = params(SinkValueParamName).asInstanceOf[LazyParameter[AnyRef]]
    val finalState = finalStateOpt.getOrElse(throw new IllegalStateException("Unexpected (not defined) final state determined during parameters validation"))
    implProvider.createSink(value, finalState.schema)
  }

}
