/*
 * Copyright 2005-2010 LAMP/EPFL
 */

package scala.tools.refactoring

import scala.tools.nsc.io.AbstractFile
import scala.tools.refactoring.common.Selections
import scala.tools.refactoring.common.EnrichedTrees
import scala.tools.refactoring.sourcegen.SourceGenerator
import scala.tools.refactoring.transformation.TreeTransformations
import scala.tools.refactoring.common.TextChange
import scala.tools.refactoring.common.TracingImpl

/**
 * The Refactoring trait combines the transformation and source generation traits with
 * their dependencies. Refactoring is mixed in by all concrete refactorings and can be
 * used by users of the library.
 */
trait Refactoring extends Selections with TreeTransformations with TracingImpl with SourceGenerator with EnrichedTrees {

  this: common.CompilerAccess =>

  /**
   * Creates a list of changes from a list of (potentially changed) trees.
   *
   * @param A list of trees that are to be searched for modifications.
   * @return A list of changes that can be applied to the source file.
   */
  def refactor(changed: List[global.Tree]): List[TextChange] = context("main") {
    val changes = createChanges(changed)
    changes map minimizeChange
  }

  /**
   * Creates changes by applying a transformation to the root tree of an
   * abstract file.
   */
  def transformFile(file: AbstractFile, transformation: Transformation[global.Tree, global.Tree]): List[TextChange] = {
    refactor(transformation(abstractFileToTree(file)).toList)
  }

  /**
   * Creates changes by applying several transformations to the root tree
   * of an abstract file.
   * Each transformation creates a new root tree that is used as input of
   * the next transformation.
   */
  def transformFile(file: AbstractFile, transformations: List[Transformation[global.Tree, global.Tree]]): List[TextChange] = {
    def inner(root: global.Tree, ts: List[Transformation[global.Tree, global.Tree]]): Option[global.Tree] = {
      ts match {
        case t :: rest =>
          t(root) match {
            case Some(newRoot) => inner(newRoot, rest)
            case None => None
          }
        case Nil => Some(root)
      }
    }

    refactor(inner(abstractFileToTree(file), transformations).toList)
  }

  /**
   * Makes a generated change as small as possible by eliminating the
   * common pre- and suffix between the change and the source file.
   */
  private def minimizeChange(change: TextChange): TextChange = change match {
    case TextChange(file, from, to, changeText) =>

      def commonPrefixLength(s1: Seq[Char], s2: Seq[Char]) =
        (s1 zip s2 takeWhile Function.tupled(_ == _)).length

      val original = java.nio.CharBuffer.wrap(file.content).subSequence(from, to).toString
      val replacement = changeText

      val commonStart = commonPrefixLength(original, replacement)
      val commonEnd = commonPrefixLength(original.substring(commonStart).reverse, replacement.substring(commonStart).reverse)

      val minimizedChangeText = changeText.subSequence(commonStart, changeText.length - commonEnd).toString
      TextChange(file, from + commonStart, to - commonEnd, minimizedChangeText)
  }
}
