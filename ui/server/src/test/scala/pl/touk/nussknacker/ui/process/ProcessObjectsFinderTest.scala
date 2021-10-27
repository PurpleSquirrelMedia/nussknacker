package pl.touk.nussknacker.ui.process

import org.scalatest.prop.TableDrivenPropertyChecks
import org.scalatest.{FunSuite, Matchers}
import pl.touk.nussknacker.engine.api.component.ComponentId
import pl.touk.nussknacker.engine.api.deployment.ProcessActionType
import pl.touk.nussknacker.engine.api.process.VersionId
import pl.touk.nussknacker.engine.api.{FragmentSpecificData, MetaData}
import pl.touk.nussknacker.engine.build.{EspProcessBuilder, GraphBuilder}
import pl.touk.nussknacker.engine.canonicalgraph.canonicalnode.FlatNode
import pl.touk.nussknacker.engine.canonicalgraph.{CanonicalProcess, canonicalnode}
import pl.touk.nussknacker.engine.graph.node.SubprocessInputDefinition.{SubprocessClazzRef, SubprocessParameter}
import pl.touk.nussknacker.engine.graph.node.{Case, CustomNode, SubprocessInputDefinition, SubprocessOutputDefinition}
import pl.touk.nussknacker.engine.testing.ProcessDefinitionBuilder.ObjectProcessDefinition
import pl.touk.nussknacker.restmodel.processdetails.ProcessAction
import pl.touk.nussknacker.ui.api.helpers.ProcessTestData._
import pl.touk.nussknacker.ui.api.helpers.{TestProcessUtil, TestProcessingTypes}
import pl.touk.nussknacker.ui.process.marshall.ProcessConverter

import java.time.LocalDateTime

class ProcessObjectsFinderTest extends FunSuite with Matchers with TableDrivenPropertyChecks {

  import TestProcessingTypes._
  import pl.touk.nussknacker.engine.spel.Implicits._

  val subprocess = CanonicalProcess(MetaData("subProcess1", FragmentSpecificData()), null,
    List(
      canonicalnode.FlatNode(SubprocessInputDefinition("start", List(SubprocessParameter("ala", SubprocessClazzRef[String])))),
      canonicalnode.FlatNode(CustomNode("f1", None, otherExistingStreamTransformer2, List.empty)), FlatNode(SubprocessOutputDefinition("out1", "output", List.empty))), List.empty
  )

  val subprocessDetails = toDetails(ProcessConverter.toDisplayable(subprocess, TestProcessingTypes.Streaming))

  private val process1 = toDetails(TestProcessUtil.toDisplayable(
    EspProcessBuilder.id("fooProcess1").exceptionHandler()
      .source("source", existingSourceFactory)
      .customNode("custom", "out1", existingStreamTransformer)
      .customNode("custom2", "out2", otherExistingStreamTransformer)
      .emptySink("sink", existingSinkFactory)))

  private val process1deployed = process1.copy(lastAction = Option(ProcessAction(VersionId(1), LocalDateTime.now(), "user", ProcessActionType.Deploy, Option.empty, Option.empty, Map.empty)))

  private val process2 = toDetails(TestProcessUtil.toDisplayable(
    EspProcessBuilder.id("fooProcess2").exceptionHandler()
      .source("source", existingSourceFactory)
      .customNode("custom", "out1", otherExistingStreamTransformer)
      .emptySink("sink", existingSinkFactory)))

  private val process3 = toDetails(TestProcessUtil.toDisplayable(
    EspProcessBuilder.id("fooProcess3").exceptionHandler()
      .source("source", existingSourceFactory)
      .emptySink("sink", existingSinkFactory)))

  private val process4 = toDetails(TestProcessUtil.toDisplayable(
    EspProcessBuilder.id("fooProcess4").exceptionHandler()
      .source("source", existingSourceFactory)
      .subprocessOneOut("sub", "subProcess1", "output", "ala" -> "'makota'")
      .emptySink("sink", existingSinkFactory)))

  private val processWithSomeBasesStreaming = toDetails(TestProcessUtil.toDisplayable(
    EspProcessBuilder.id("processWithSomeBasesStreaming").exceptionHandler()
      .source("source", existingSourceFactory)
      .filter("checkId", "#input.id != null")
      .switch("switch", "#input.id != null", "output",
        Case("'1'", GraphBuilder.emptySink("out1", existingSinkFactory)),
        Case("'2'", GraphBuilder.emptySink("out2", existingSinkFactory2))
      )
    ))

  private val processWithSomeBasesFraud = toDetails(TestProcessUtil.toDisplayable(
    EspProcessBuilder.id("processWithSomeBasesStandalone").exceptionHandler()
      .source("source", existingSourceFactory)
      .filter("checkId", "#input.id != null")
      .switch("switch", "#input.id != null", "output",
        Case("'1'", GraphBuilder.emptySink("out1", existingSinkFactory)),
        Case("'2'", GraphBuilder.emptySink("out2", existingSinkFactory2))
      ), TestProcessingTypes.Fraud
  ))

  private val invalidProcessWithAllObjects = toDetails(TestProcessUtil.toDisplayable(
    EspProcessBuilder.id("processWithAllObjects").exceptionHandler()
      .source("source", existingSourceFactory)
      .subprocessOneOut("sub", "subProcess1", "output", "ala" -> "'makota'")
      .customNode("custom", "out1", existingStreamTransformer)
      .customNode("custom2", "out2", otherExistingStreamTransformer)
      .processor("processor1", existingServiceId)
      .processor("processor2", otherExistingServiceId)
      .filter("filterInvalid", "#variableThatDoesNotExists == 1")
      .emptySink("sink", existingSinkFactory)))

  test("should find processes for queries") {
    val queriesForProcesses = ProcessObjectsFinder.findQueries(List(process1, process2, process3, process4, subprocessDetails), List(processDefinition))

    queriesForProcesses shouldBe Map(
      "query1" -> List(process1.id),
      "query2" -> List(process1.id),
      "query3" -> List(process1.id, process2.id),
      "query4" -> List(process4.id),
      "query5" -> List.empty
    )
  }

  test("should find processes for transformers") {
    val table = Table(
      ("transformers", "expectedProcesses"),
      (Set(existingStreamTransformer), List(process1.id)),
      (Set(otherExistingStreamTransformer), List(process1.id, process2.id)),
      (Set(otherExistingStreamTransformer2), List(process4.id)),
      (Set(existingStreamTransformer, otherExistingStreamTransformer, otherExistingStreamTransformer2), List(process1.id, process2.id, process4.id)),
      (Set("garbage"), List())
    )
    forAll(table) { (transformers, expectedProcesses) =>
      val definition = processDefinition.withSignalsWithTransformers("signal1", classOf[String], transformers)
      val signalDefinition = ProcessObjectsFinder.findSignals(List(process1, process2, process3, process4, subprocessDetails), List(definition))
      signalDefinition should have size 1
      signalDefinition("signal1").availableProcesses shouldBe expectedProcesses
    }
  }

  test("should find unused components") {
    val table = Table(
      ("processes", "unusedComponents"),
      (List(invalidProcessWithAllObjects), List("fooProcessor", "fooService2", "fooService3", "fooService4", "fooSource", "notBlank", optionalEndingStreamTransformer)),
      (List(process1, process4), List("barService", "fooProcessor", "fooService", "fooService2", "fooService3", "fooService4", "fooSource", "notBlank", optionalEndingStreamTransformer)),
      (List(process1), List("barService", "fooProcessor", "fooService", "fooService2", "fooService3", "fooService4", "fooSource",  "notBlank", optionalEndingStreamTransformer, "subProcess1"))
    )
    forAll(table) { (processes, unusedComponents) =>
      val result = ProcessObjectsFinder.findUnusedComponents(processes ++ List(subprocessDetails), List(processDefinition))
      result shouldBe unusedComponents
    }
  }

  test("should calculate component usages") {
    val table = Table(
      ("processes", "expectedData"),
      (List.empty, Map.empty),
      (List(process2, processWithSomeBasesStreaming), Map(s"$Streaming-$existingSinkFactory" -> 2, s"$Streaming-$existingSinkFactory2" -> 1, s"$Streaming-$existingSourceFactory" -> 2, s"$Streaming-$otherExistingStreamTransformer" -> 1, "switch" -> 1, "filter" -> 1)),
      (List(process2, subprocessDetails), Map(s"$Streaming-$existingSinkFactory" -> 1, s"$Streaming-$existingSourceFactory" -> 1, s"$Streaming-$otherExistingStreamTransformer" -> 1, s"$Streaming-$otherExistingStreamTransformer2" -> 1)),
      (List(process2, processWithSomeBasesStreaming, subprocessDetails), Map(s"$Streaming-$existingSinkFactory" -> 2, s"$Streaming-$existingSinkFactory2" -> 1, s"$Streaming-$existingSourceFactory" -> 2, s"$Streaming-$otherExistingStreamTransformer" -> 1, s"$Streaming-$otherExistingStreamTransformer2" -> 1, "switch" -> 1, "filter" -> 1)),
      (List(processWithSomeBasesFraud, processWithSomeBasesStreaming), Map(
        s"$Streaming-$existingSinkFactory" -> 1, s"$Streaming-$existingSinkFactory2" -> 1, s"$Streaming-$existingSourceFactory" -> 1,
        s"$Fraud-$existingSinkFactory" -> 1, s"$Fraud-$existingSinkFactory2" -> 1, s"$Fraud-$existingSourceFactory" -> 1,
        "switch" -> 2, "filter" -> 2,
      )),
    )

    forAll(table) { (processes, expectedData) =>
      val expectedUsages = expectedData.map{ case (key, value) => ComponentId.create(key) -> value }
      val result = ProcessObjectsFinder.calculateComponentUsages(processes)
      result shouldBe expectedUsages
    }
  }

  test("should find components by componentId") {
    val processesList = List(process1deployed, process2, process3, process4, subprocessDetails)

    val componentsWithinProcesses = ProcessObjectsFinder.findComponents(processesList, "otherTransformer")
    componentsWithinProcesses shouldBe List(
      ProcessComponent("fooProcess1", "custom2", "Category", true),
      ProcessComponent("fooProcess2", "custom", "Category", false)
    )

    val componentsWithinSubprocesses = ProcessObjectsFinder.findComponents(processesList, "otherTransformer2")
    componentsWithinSubprocesses shouldBe List(
      ProcessComponent("subProcess1", "f1", "Category", false)
    )
    componentsWithinSubprocesses.map(c => c.isDeployed) shouldBe List(false)

    val componentsNotExist = ProcessObjectsFinder.findComponents(processesList, "notExistingTransformer")
    componentsNotExist shouldBe Nil
  }
}
