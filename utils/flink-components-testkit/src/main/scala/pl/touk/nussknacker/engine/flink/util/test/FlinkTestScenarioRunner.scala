package pl.touk.nussknacker.engine.flink.util.test

import com.typesafe.config.Config
import org.apache.flink.api.common.ExecutionConfig
import org.apache.flink.api.common.typeinfo.TypeInformation
import org.apache.flink.streaming.api.scala.StreamExecutionEnvironment
import pl.touk.nussknacker.engine.api.ProcessVersion
import pl.touk.nussknacker.engine.api.component.ComponentDefinition
import pl.touk.nussknacker.engine.api.process.{EmptyProcessConfigCreator, SourceFactory}
import pl.touk.nussknacker.engine.api.typed.typing.Typed
import pl.touk.nussknacker.engine.deployment.DeploymentData
import pl.touk.nussknacker.engine.flink.test.FlinkMiniClusterHolder
import pl.touk.nussknacker.engine.flink.util.source.CollectionSource
import pl.touk.nussknacker.engine.graph.EspProcess
import pl.touk.nussknacker.engine.process.ExecutionConfigPreparer
import pl.touk.nussknacker.engine.process.compiler.FlinkProcessCompilerWithTestComponents
import pl.touk.nussknacker.engine.process.helpers.SinkForType
import pl.touk.nussknacker.engine.process.registrar.FlinkProcessRegistrar
import pl.touk.nussknacker.engine.testing.LocalModelData
import pl.touk.nussknacker.engine.testmode.{TestComponentHolder, TestComponentsHolder}
import pl.touk.nussknacker.engine.util.test.TestScenarioRunner

import scala.reflect.ClassTag

object testComponents {

  def withDataList[T: ClassTag : TypeInformation](data: List[T]): List[ComponentDefinition] = List(
    "source" -> SourceFactory.noParamFromClassTag[T](new CollectionSource[T](new ExecutionConfig, data, None, Typed.apply[T])),
    "noopSource" -> SourceFactory.noParamFromClassTag[T](new CollectionSource[T](new ExecutionConfig, List.empty, None, Typed.apply[T]))
  ).map(cd => ComponentDefinition(cd._1, cd._2))
}

case class SinkForList[T]() extends SinkForType[List[T]]

class FlinkTestScenarioRunner(val components: List[ComponentDefinition], val config: Config, flinkMiniCluster: FlinkMiniClusterHolder) extends TestScenarioRunner {

  var testComponentHolder: TestComponentHolder = _

  override def runWithData[T: ClassTag, Result](scenario: EspProcess, data: List[T]): List[Result] = {

    implicit val typeInf: TypeInformation[T] = TypeInformation.of(implicitly[ClassTag[T]].runtimeClass.asInstanceOf[Class[T]])
    val modelData = LocalModelData(config, new EmptyProcessConfigCreator)
    val componentsWithData = testComponents.withDataList(data)
    val componentsWithTestComponents = this.components ++ componentsWithData
    testComponentHolder = TestComponentsHolder.registerTestComponents(componentsWithTestComponents)

    //todo get flink mini cluster through composition
    val env = flinkMiniCluster.createExecutionEnvironment()
    val registrar = FlinkProcessRegistrar(new FlinkProcessCompilerWithTestComponents(testComponentHolder, modelData), ExecutionConfigPreparer.unOptimizedChain(modelData))
    registrar.register(new StreamExecutionEnvironment(env), scenario, ProcessVersion.empty, DeploymentData.empty, Some(testComponentHolder.runId))
    env.executeAndWaitForFinished(scenario.id)()
    testComponentHolder.results(testComponentHolder.runId).map((k: Any) => k.asInstanceOf[Result])
  }

}

object NuTestScenarioRunner {
  def flinkBased(config: Config, flinkMiniCluster: FlinkMiniClusterHolder): FlinkTestScenarioRunnerBuilder = {
    FlinkTestScenarioRunnerBuilder(List(), config, flinkMiniCluster)
  }
}

case class FlinkTestScenarioRunnerBuilder(components: List[ComponentDefinition], config: Config, flinkMiniCluster: FlinkMiniClusterHolder) {
  def build() = new FlinkTestScenarioRunner(components, config, flinkMiniCluster)

  def withExtraComponents(components: List[ComponentDefinition]): FlinkTestScenarioRunnerBuilder = {
    copy(components = components)
  }
}