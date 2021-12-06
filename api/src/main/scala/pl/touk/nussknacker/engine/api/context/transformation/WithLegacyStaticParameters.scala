package pl.touk.nussknacker.engine.api.context.transformation

import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.NodeId
import pl.touk.nussknacker.engine.api.context.ValidationContext
import pl.touk.nussknacker.engine.api.definition.Parameter

// TODO: Deprecated API - remove it after clean up
// The purpose of this trait is to give ability to return static parameters next to existing dynamic one.
// It is mainly to make Admin tab -> Invoke service form be usable during transition to new, dynamic form
trait WithLegacyStaticParameters { self: GenericNodeTransformation[_] =>

  def staticParameters: List[Parameter]

}

trait SingleInputLegacyStaticParametersBasedOnDynamicParameters extends WithLegacyStaticParameters { self: SingleInputGenericNodeTransformation[_] =>
  override def staticParameters: List[Parameter] = {
    self.contextTransformation(ValidationContext.empty, List.empty)(NodeId("fakeId"))(TransformationStep(List.empty, None))  match {
      case NextParameters(params, _, _) =>
        params
      case FinalResults(_, _, _) =>
        List.empty
    }
  }
}

trait  JoinLegacyStaticParametersBasedOnDynamicParameters extends WithLegacyStaticParameters { self: JoinGenericNodeTransformation[_] =>
  override def staticParameters: List[Parameter] = {
    self.contextTransformation(Map.empty, List.empty)(NodeId("fakeId"))(TransformationStep(List.empty, None))  match {
      case NextParameters(params, _, _) =>
        params
      case FinalResults(_, _, _) =>
        List.empty
    }
  }
}