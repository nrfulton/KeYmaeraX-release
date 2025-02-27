/**
 * Copyright (c) Carnegie Mellon University.
 * See LICENSE.txt for the conditions of this license.
 */
package edu.cmu.cs.ls.keymaerax.hydra.responses.tools

import edu.cmu.cs.ls.keymaerax.btactics.TactixLibrary
import edu.cmu.cs.ls.keymaerax.core.{And, ComparisonFormula, Expression, False, Formula, FuncOf, Imply, NamedSymbol, PredOf, Sequent, StaticSemantics, Term, True, Variable}
import edu.cmu.cs.ls.keymaerax.hydra.{Response, UIKeYmaeraXPrettyPrinter}
import edu.cmu.cs.ls.keymaerax.infrastruct.{ExpressionTraversal, PosInExpr}
import spray.json.{JsArray, JsNull, JsObject, JsString}

import scala.collection.immutable
import scala.util.matching.Regex.Match

class CounterExampleResponse(kind: String, assumptions: Option[Formula], fml: Formula = True, cex: Map[NamedSymbol, Expression] = Map()) extends Response {
  def getJson: JsObject = {
    val bv = StaticSemantics.boundVars(fml).toSet[NamedSymbol]
    val (boundCex, freeCex) = cex.partition(e => bv.contains(e._1))
    JsObject(
      "result" -> JsString(kind),
      "origFormula" -> JsString(fml.prettyString.replaceAllLiterally("()", "")),
      "additionalAssumptions" -> assumptions.map(f => JsString(f.prettyString.replaceAllLiterally("()", ""))).getOrElse(JsNull),
      "cexFormula" -> JsString(createCexFormula(fml, cex).replaceAllLiterally("()", "")),
      "cexValues" -> JsArray(
        freeCex.map(e => JsObject(
          "symbol" -> JsString(e._1.prettyString.replaceAllLiterally("()", "")),
          "value" -> JsString(e._2.prettyString.replaceAllLiterally("()", "")))
        ).toList:_*
      ),
      "speculatedValues" -> JsArray(
        boundCex.map(e => JsObject(
          "symbol" -> JsString(e._1.prettyString.replaceAllLiterally("()", "")),
          "value" -> JsString(e._2.prettyString.replaceAllLiterally("()", "")))
        ).toList:_*
      )
    )
  }

  private def createCexFormula(fml: Formula, cex: Map[NamedSymbol, Expression]): String = {
    def replaceWithCexVals(fml: Formula, cex: Map[NamedSymbol, Expression]): Formula = {
      ExpressionTraversal.traverse(new ExpressionTraversal.ExpressionTraversalFunction {
        override def preT(p: PosInExpr, t: Term): Either[Option[ExpressionTraversal.StopTraversal], Term] = t match {
          case v: Variable if cex.contains(v) => Right(cex(v).asInstanceOf[Term])
          case FuncOf(fn, _) if cex.contains(fn) => Right(cex(fn).asInstanceOf[Term])
          case _ => Left(None)
        }

        override def preF(p: PosInExpr, f: Formula): Either[Option[ExpressionTraversal.StopTraversal], Formula] = f match {
          case PredOf(fn, _) =>
            if (cex.contains(fn)) Right(cex(fn).asInstanceOf[Formula])
            else Left(None)
          case _ => Left(None)
        }
      }, fml).get
    }

    if (cex.nonEmpty & cex.forall(_._2.isInstanceOf[Term])) {
      val Imply(assumptions, conclusion) = fml

      //@note flag false comparison formulas `cmp` with (cmp<->false)
      val cexConclusion = ExpressionTraversal.traverse(new ExpressionTraversal.ExpressionTraversalFunction {
        private def makeSeq(fml: Formula): Sequent = Sequent(immutable.IndexedSeq(), immutable.IndexedSeq(fml))

        override def preF(p: PosInExpr, f: Formula): Either[Option[ExpressionTraversal.StopTraversal], Formula] = f match {
          case cmp: ComparisonFormula =>
            val cexCmp = TactixLibrary.proveBy(replaceWithCexVals(cmp, cex), TactixLibrary.RCF)
            if (cexCmp.subgoals.size > 1 || cexCmp.subgoals.headOption.getOrElse(makeSeq(True)) == makeSeq(False)) {
              Right(And(False, And(cmp, False)))
            } else Right(cmp)
          case _ => Left(None)
        }
      }, conclusion).get

      val cexFml = UIKeYmaeraXPrettyPrinter.htmlEncode(Imply(assumptions, cexConclusion).prettyString)

      //@note look for variables at word boundary (do not match in the middle of other words, do not match between &;)
      val symMatcher = s"(${cex.keySet.map(_.prettyString).mkString("|")})(?![^&]*;)\\b".r("v")
      val cexFmlWithVals = symMatcher.replaceAllIn(cexFml, (m: Match) => {
        val cexSym = UIKeYmaeraXPrettyPrinter.htmlEncode(m.group("v"))
        if ((m.before + cexSym).endsWith("false")) {
          cexSym
        } else {
          val cexVal = UIKeYmaeraXPrettyPrinter.htmlEncode(cex.find(_._1.prettyString == cexSym).get._2.prettyString)
          s"""<div class="k4-cex-fml"><ul><li>$cexVal</li></ul>$cexSym</div>"""
        }
      })

      //@note look for (false & cexCmp & false) groups and replace with boldface danger spans
      val cexMatcher = "false&and;(.*?)&and;false".r("fml")
      cexMatcher.replaceAllIn(cexFmlWithVals, (m: Match) => {
        val cexCmp = m.group("fml")
        s"""<div class="k4-cex-highlight text-danger">$cexCmp</div>"""
      })
    } else {
      replaceWithCexVals(fml, cex).prettyString
    }
  }
}
