package pl.touk.nussknacker.engine.types

import java.util
import java.util.regex.Pattern

import org.scalatest.prop.TableDrivenPropertyChecks._
import org.scalatest.{FunSuite, Matchers, OptionValues}
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, TypedClass}
import pl.touk.nussknacker.engine.api.process.PropertyFromGetterExtractionStrategy.{AddPropertyNextToGetter, DoNothing, ReplaceGetterWithProperty}
import pl.touk.nussknacker.engine.api.process.{ClassExtractionSettings, ClassMemberPatternPredicate, PropertyFromGetterExtractionStrategy, SuperClassPatternPredicate}
import pl.touk.nussknacker.engine.api.{Documentation, Hidden, HideToString, ParamName}
import pl.touk.nussknacker.engine.definition.TypeInfos.{ClazzDefinition, MethodInfo, Parameter}
import pl.touk.nussknacker.engine.spel.SpelExpressionRepr
import pl.touk.nussknacker.engine.types.TypesInformationExtractor._

import scala.annotation.meta.getter
import scala.concurrent.Future
import scala.reflect.runtime.universe._

class EspTypeUtilsSpec extends FunSuite with Matchers with OptionValues {

  val signatures = Table(("signature", "value", "matches"),
    (java.lang.Boolean.TYPE, classOf[java.lang.Boolean], true),
    (java.lang.Long.TYPE, classOf[java.lang.Long], true),
    (java.lang.Integer.TYPE, classOf[java.lang.Integer], true),
    (classOf[java.lang.Long], classOf[java.lang.Integer], true),
    (classOf[java.lang.Long], java.lang.Integer.TYPE, true),
    (java.lang.Long.TYPE, java.lang.Integer.TYPE, true),
    (java.lang.Long.TYPE, classOf[java.lang.Integer], true),
    (java.lang.Long.TYPE, classOf[java.lang.Integer], true),

    (java.lang.Character.TYPE, classOf[java.lang.Character], true),
    (classOf[java.lang.Number], classOf[java.lang.Integer], true),
    (java.lang.Integer.TYPE, classOf[java.lang.Long], false)
  )

  test("should check if signature is possible") {

    forAll(signatures) { (signature, value, matches) =>
      EspTypeUtils.signatureElementMatches(signature, value) shouldBe matches
    }
  }

  case class SampleClass(foo: Int, bar: String) extends SampleAbstractClass with SampleInterface

  class Returning {
    def futureOfList: Future[java.util.List[SampleClass]] = ???
  }

  test("should extract generic return type parameters") {

    val method = classOf[Returning].getMethod("futureOfList")

    val extractedType = EspTypeUtils.getGenericType(method.getGenericReturnType).get

    extractedType shouldBe Typed.genericTypeClass[java.util.List[_]](List(Typed[SampleClass]))
  }

  test("should extract public fields from scala case class") {
    val sampleClassInfo = singleClassDefinition[SampleClass]()

    sampleClassInfo.value.methods shouldBe Map(
      "foo" -> List(MethodInfo(List.empty, Typed(Integer.TYPE), None)),
      "bar" -> List(MethodInfo(List.empty, Typed[String], None)),
      "toString" -> List(MethodInfo(List(), Typed[String], None))
    )
  }

  test("should extract generic field") {
    val sampleClassInfo = singleClassDefinition[JavaClassWithGenericField]()

    sampleClassInfo.value.methods("list").head.refClazz shouldEqual Typed.fromDetailedType[java.util.List[String]]
  }

  test("shoud detect java beans and fields in java class") {
    def methods(strategy: PropertyFromGetterExtractionStrategy) =
      singleClassDefinition[JavaSampleClass](ClassExtractionSettings.Default.copy(propertyExtractionStrategy = strategy)).value.methods.keys.toSet

    val methodsForAddPropertyNextToGetter = methods(AddPropertyNextToGetter)
    methodsForAddPropertyNextToGetter   shouldEqual Set("foo", "bar", "getBeanProperty", "isBooleanProperty", "getNotProperty", "toString", "beanProperty", "booleanProperty")

    val methodsForReplaceGetterWithProperty = methods(ReplaceGetterWithProperty)
    methodsForReplaceGetterWithProperty shouldEqual Set("foo", "bar", "beanProperty", "booleanProperty", "getNotProperty", "toString")

    val methodsForDoNothing = methods(DoNothing)
    methodsForDoNothing                 shouldEqual Set("foo", "bar", "getBeanProperty", "isBooleanProperty", "getNotProperty", "toString")
  }

  test("should skip hidden properties") {
    val testTypes = Table(("type", "className"),
      (Typed[SampleClass], "SampleClass"),
      (Typed[JavaSampleClass], "JavaSampleClass")
    )

    val testClassPatterns = Table("classPattern",
      ".*SampleClass",
      ".*SampleAbstractClass",
      ".*SampleInterface"
    )

    forAll(testTypes) { (clazz, clazzName) =>
      forAll(testClassPatterns) { classPattern =>
        val infos = clazzAndItsChildrenDefinition(List(clazz))(ClassExtractionSettings.Default.copy(excludeClassMemberPredicates =
          ClassExtractionSettings.DefaultExcludedMembers ++ Seq(
          ClassMemberPatternPredicate(SuperClassPatternPredicate(Pattern.compile(classPattern)), Pattern.compile("ba.*")),
          ClassMemberPatternPredicate(SuperClassPatternPredicate(Pattern.compile(classPattern)), Pattern.compile("get.*")),
          ClassMemberPatternPredicate(SuperClassPatternPredicate(Pattern.compile(classPattern)), Pattern.compile("is.*"))
        )))
        val sampleClassInfo = infos.find(_.clazzName.asInstanceOf[TypedClass].klass.getName.contains(clazzName)).get

        sampleClassInfo.methods shouldBe Map(
          "toString" -> List(MethodInfo(List(), Typed[String], None)),
          "foo" -> List(MethodInfo(List.empty, Typed(Integer.TYPE), None))
        )
      }
    }
  }

  test("should extract parameters from embedded lists") {

    val typeUtils = singleClassAndItsChildrenDefinition[Embeddable]()

    typeUtils.find(_.clazzName == Typed[TestEmbedded]) shouldBe Some(ClazzDefinition(Typed[TestEmbedded], Map(
      "string" -> List(MethodInfo(List(), Typed[String], None)),
      "javaList" -> List(MethodInfo(List(), Typed.fromDetailedType[java.util.List[String]], None)),
      "scalaList" -> List(MethodInfo(List(), Typed.fromDetailedType[List[String]], None)),
      "javaMap" -> List(MethodInfo(List(), Typed.fromDetailedType[java.util.Map[String, String]], None)),
      "toString" -> List(MethodInfo(List(), Typed[String], None))
    )))

  }

  test("should not discover hidden fields") {
    val typeUtils = singleClassDefinition[ClassWithHiddenFields]()

    typeUtils shouldBe Some(ClazzDefinition(Typed[ClassWithHiddenFields], Map(
      "normalField" -> List(MethodInfo(List(), Typed[String], None)),
      "normalParam" -> List(MethodInfo(List(), Typed[String], None)),
      "toString" -> List(MethodInfo(List(), Typed[String], None))
    )))
  }

  test("should skip toString method when HideToString implemented") {
    val hiddenToStringClasses = Table("class", classOf[JavaBannedToStringClass], classOf[BannedToStringClass])
    forAll(hiddenToStringClasses) { EspTypeUtils.clazzDefinition(_)(ClassExtractionSettings.Default)
      .methods.keys shouldNot contain("toString")
    }
  }

  class BannedToStringClass extends HideToString

  case class ScalaSampleDocumentedClass() {

    val field1: Long = 123L

    //we use this @getter annotation here, because vals in case classes are translated by default to private field and public getter method for that field
    //we just annotate public getter method (that will be created by scalac) for field2
    //more here: https://www.scala-lang.org/api/current/scala/annotation/meta/index.html
    @(Documentation @getter)(description = ScalaSampleDocumentedClass.field2Docs)
    val field2: Long = 234L

    def foo(fooParam1: String): Long = {
      0L
    }

    def bar(@ParamName("barparam1") barparam1: Long): String = {
      ""
    }
    @Documentation(description = ScalaSampleDocumentedClass.bazDocs)
    def baz(@ParamName("bazparam1") bazparam1: String, @ParamName("bazparam2") bazparam2: Int): Long = {
      0L
    }

    @Documentation(description = ScalaSampleDocumentedClass.quxDocs)
    def qux(quxParam1: String): Long = {
      0L
    }
  }

  case class TestEmbedded(string: String, javaList: java.util.List[String], scalaList: List[String], javaMap: java.util.Map[String, String])

  class Embeddable {

    def data: Future[List[TestEmbedded]] = ???

  }

  case class ClassWithHiddenFields(@(Hidden @getter)imnothereCaseClassParam: String, normalParam: String) {

    @Hidden
    def imnothereMethod(par: String): String = par

    @(Hidden @getter)
    val imnothereField: String = ""

    val normalField: String = ""
  }

  object ScalaSampleDocumentedClass {
    final val field2Docs = "This is sample documentation for field2 field"
    final val bazDocs = "This is sample documentation for baz method"
    final val quxDocs = "This is sample documentation for qux method"
  }

  test("should extract description and params from method") {
    val scalaClazzInfo = singleClassDefinition[ScalaSampleDocumentedClass]().value

    val javaClazzInfo = singleClassDefinition[JavaSampleDocumentedClass]().value

    val table = Table(
      ("method", "methodInfo"),
      //FIXME: scala 2.11, 2.12 have different behaviour - named parameters are extracted differently :/
      //("foo", MethodInfo(parameters = List(param[String]("fooParam1")), refClazz = Typed[Long], description = None)),
      ("bar", List(MethodInfo(parameters = List(param[Long]("barparam1")), refClazz = Typed[String], description = None))),
      ("baz", List(MethodInfo(parameters = List(param[String]("bazparam1"), param[Int]("bazparam2")), refClazz = Typed[Long], description = Some(ScalaSampleDocumentedClass.bazDocs)))),
      //FIXME: scala 2.11, 2.12 have different behaviour - named parameters are extracted differently :/
      //("qux", MethodInfo(parameters = List(param[String]("quxParam1")), refClazz = Typed[Long], description = Some(ScalaSampleDocumentedClass.quxDocs))),
      ("field1", List(MethodInfo(parameters = List.empty, refClazz = Typed[Long], description = None))),
      ("field2", List(MethodInfo(parameters = List.empty, refClazz = Typed[Long], description = Some(ScalaSampleDocumentedClass.field2Docs))))
    )
    forAll(table){ case (method, methodInfo) =>
        scalaClazzInfo.methods(method) shouldBe methodInfo
        javaClazzInfo.methods(method) shouldBe methodInfo
    }
  }

  test("enabled by default classes") {
    val emptyDef = singleClassAndItsChildrenDefinition[EmptyClass]()
    // We want to use boxed primitive classes even if they wont be discovered in any place
    val boxedIntDef = emptyDef.find(_.clazzName == Typed[Integer])
    boxedIntDef shouldBe defined
  }

  test("extract overloaded methods") {
    val cl = singleClassDefinition[ClassWithOverloadedMethods]().value
    val methods = cl.methods("method")
    methods should have size 3
    methods.map(_.parameters.head.refClazz).toSet shouldEqual Set(Typed[Int], Typed[Boolean], Typed[String])
  }

  test("hidden by default classes") {
    val metaSpelDef = singleClassAndItsChildrenDefinition[ServiceWithMetaSpelParam]()
    // These params are used programmable - user can't create instance of this type
    metaSpelDef.exists(_.clazzName == Typed[SpelExpressionRepr]) shouldBe false
  }

  val listMethods = Table("methodName", "indexOf", "contains", "isEmpty", "size")
  val mapMethods = Table("methodName", "get", "isEmpty", "size", "values")
  val optionMethods = Table("methodName", "get", "contains", "isEmpty")

  test("should extract basic methods from standard collection types") {
    forAll(mapMethods) { methodName =>
      val javaMapDef = singleClassDefinition[util.Map[_, _]]().value
      javaMapDef.methods.get(methodName) shouldBe defined

      val scalaMapDef = singleClassDefinition[Map[_, _]]().value
      scalaMapDef.methods.get(methodName) shouldBe defined

    }
    forAll(listMethods) { methodName =>
      val javaListDef = singleClassDefinition[util.List[_]]().value
      javaListDef.methods.get(methodName) shouldBe defined

      val scalaListDef = singleClassDefinition[List[_]]().value
      scalaListDef.methods.get(methodName) shouldBe defined
    }

    forAll(optionMethods) { methodName =>
      val scalaOptionDef = singleClassDefinition[Option[_]]().value
      scalaOptionDef.methods.get(methodName) shouldBe defined
    }
  }

  test("should hide some ugly presented methods") {
    val classDef = singleClassDefinition[ClassWithWeirdTypesInMethods]().value
    classDef.methods.get("methodReturningArray") shouldBe empty
    classDef.methods.get("methodWithDollar$") shouldBe empty
    classDef.methods.get("normalMethod") shouldBe defined
  }

  class EmptyClass {
    def invoke(): Unit = ???
  }

  class ClassWithWeirdTypesInMethods {
    def methodReturningArray: Array[Int] = ???
    def methodWithDollar$: String = ???
    def normalMethod: String = ???
  }

  class ClassWithOverloadedMethods {
    def method(i: Int): String = ???
    def method(i: String): String = ???
    def method(i: Boolean): String = ???
  }

  class ServiceWithMetaSpelParam {
    def invoke(@ParamName("expression") expr: SpelExpressionRepr): Unit = ???
  }

  private def param[T: TypeTag](name: String): Parameter = {
    Parameter(name, Typed.fromDetailedType[T])
  }

  private def singleClassDefinition[T: TypeTag](settings: ClassExtractionSettings = ClassExtractionSettings.Default): Option[ClazzDefinition] = {
    val ref = Typed.fromDetailedType[T]
    // ClazzDefinition has clazzName with generic parameters but they are always empty so we need to compare name without them
    clazzAndItsChildrenDefinition(List(Typed(ref)))(settings).find(_.clazzName.asInstanceOf[TypedClass].klass == ref.asInstanceOf[TypedClass].klass)
  }

  private def singleClassAndItsChildrenDefinition[T: TypeTag](settings: ClassExtractionSettings = ClassExtractionSettings.Default) = {
    val ref = Typed.fromDetailedType[T]
    clazzAndItsChildrenDefinition(List(Typed(ref)))(settings)
  }

}
