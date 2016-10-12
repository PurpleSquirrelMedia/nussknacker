package pl.touk.esp.engine.spel

import cats.data.Validated
import org.springframework.expression.Expression
import org.springframework.expression.spel.ast.{CompoundExpression, PropertyOrFieldReference, VariableReference}
import org.springframework.expression.spel.{SpelNode, standard}
import pl.touk.esp.engine.compile.ValidationContext
import pl.touk.esp.engine.compiledgraph.expression.ExpressionParseError
import pl.touk.esp.engine.definition.DefinitionExtractor.ClazzRef

class SpelExpressionValidator(expr: Expression, ctx: ValidationContext) {
  import SpelExpressionValidator._

  private val ignoredTypes: Set[ClazzRef] = Set(
    classOf[java.util.Map[_, _]],
    classOf[scala.collection.convert.Wrappers.MapWrapper[_, _]],
    classOf[Any]
  ).map(ClazzRef.apply)

  def validate(): Validated[ExpressionParseError, Expression] = {
    val ast = expr.asInstanceOf[standard.SpelExpression].getAST
    resolveReferences(ast).andThen { _ =>
      val propertyAccesses = findAllPropertyAccess(ast)
      propertyAccesses.flatMap(validatePropertyAccess).headOption match {
        case Some(error) => Validated.invalid(error)
        case None => Validated.valid(expr)
      }
    }
  }

  private def resolveReferences(node: SpelNode): Validated[ExpressionParseError, Expression] = {
    val references = findAllVariableReferences(node)
    val notResolved = references.filterNot(ctx.contains)
    if (notResolved.isEmpty) Validated.valid(expr)
    else Validated.Invalid(ExpressionParseError(s"Unresolved references ${notResolved.mkString(", ")}"))
  }

  private def findAllPropertyAccess(n: SpelNode): List[SpelPropertyAccess] = {
    n match {
      case ce: CompoundExpression if ce.childrenHead.isInstanceOf[VariableReference] =>
        val children = ce.children.toList
        val variableName = children.head.toStringAST.tail
        val clazzRef = ctx.apply(variableName)
        if (ignoredTypes.contains(clazzRef)) List.empty //odpuszczamy na razie walidowanie spelowych Map i wtedy kiedy nie jestesmy pewni typu
        else {
          val references = children.tail.takeWhile(_.isInstanceOf[PropertyOrFieldReference]).map(_.toStringAST) //nie bierzemy jeszcze wszystkich co nie jest do konca poprawnne, np w `#obj.children.?[id == '55'].empty`
          List(SpelPropertyAccess(variableName, references, ctx.apply(variableName)))
        }
      case _ =>
        n.children.flatMap(c => findAllPropertyAccess(c)).toList
    }
  }


  private def validatePropertyAccess(propAccess: SpelPropertyAccess): Option[ExpressionParseError] = {
    def checkIfPropertiesExistsOnClass(propsToGo: List[String], clazz: ClazzRef): Option[ExpressionParseError] = {
      if (propsToGo.isEmpty) None
      else {
        val currentProp = propsToGo.head
        val typeInfo = ctx.getTypeInfo(clazz)
        typeInfo.getMethod(currentProp) match {
          case Some(anotherClazzRef) => checkIfPropertiesExistsOnClass(propsToGo.tail, anotherClazzRef)
          case None => Some(ExpressionParseError(s"There is no property '$currentProp' in type '${typeInfo.clazzName.refClazzName}'"))
        }
      }
    }
    checkIfPropertiesExistsOnClass(propAccess.properties, propAccess.clazz)
  }

  private def findAllVariableReferences(n: SpelNode): List[String] = {
    if (n.getChildCount == 0) {
      n match {
        case vr: VariableReference => List(vr.toStringAST.tail)
        case _ => List()
      }
    }
    else n.children.flatMap(findAllVariableReferences).toList
  }

}

object SpelExpressionValidator {

  implicit class RichSpelNode(n: SpelNode) {
    def children: Seq[SpelNode] = {
      (0 until n.getChildCount).map(i => n.getChild(i))
    }
    def childrenHead: SpelNode = {
      n.getChild(0)
    }

  }

  case class SpelPropertyAccess(variable: String, properties: List[String], clazz: ClazzRef)
}