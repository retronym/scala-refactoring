/*
 * Copyright 2005-2010 LAMP/EPFL
 */
// $Id$

package scala.tools.refactoring

import scala.tools.nsc.util.SourceFile
import scala.tools.nsc.interactive.Global
import scala.tools.nsc.io.AbstractFile
import scala.tools.refactoring.analysis.Analysis
import scala.tools.refactoring.analysis.FullIndexes
import scala.tools.refactoring.regeneration.{FragmentRepository, Regeneration}
import scala.tools.refactoring.transformation.Transformation
import scala.tools.refactoring.common.{Selections, Tracing, LayoutPreferences, SilentTracing}
import scala.tools.refactoring.common.Change

abstract class Refactoring extends Analysis with Transformation with Regeneration with Selections with Tracing with LayoutPreferences with FullIndexes {

  val global: Global
  
  private val originalFragments: SourceFile => FragmentRepository = {
    val fragments = new collection.mutable.HashMap[SourceFile, FragmentRepository]
    file => {
      def body = global.unitOf(file).body
      def notLoaded = body == global.EmptyTree 
      
      if(notLoaded) {
        global.reloadSources(file :: Nil)
        if(notLoaded)
          throw RefactoringError("Compilation Unit for "+ file.file.name +" not ready.")
      }
      
      fragments.getOrElseUpdate(
        file, 
        new FragmentRepository(splitIntoFragments(body) \\ (trace("Original: %s", _))))
    }
  }
  
  private def hasTreeChanged(changed: List[global.Tree])(tree: global.Tree) = changed.contains(tree) || changed.exists( t => tree.pos.includes(t.pos))
  
  private def createChangeText(tree: global.Tree, changed: TreeModifications) = {
      
    val fr = originalFragments(tree.pos.source)
      
    val modifiedFragments = essentialFragments(tree, fr) \\ (trace("Modified: %s", _))
      
    val change = merge(modifiedFragments, fr, hasTreeChanged(changed.allChangedTrees)) mkString
    
    minimizeChange(tree.pos.source, change, modifiedFragments.start, modifiedFragments.end)
  }
  
  private def minimizeChange(source: SourceFile, text: String, from: Int, to: Int) = {
    
    def commonLength[T](l1: Seq[T], l2: Seq[T], length: Int = 0): (Int, Seq[T], Seq[T]) = (l1, l2) match {
      case (x :: xs, y :: ys) if x == y => commonLength(xs, ys, length + 1)
      case (x, y) => (length, x, y)
    }
    
    val originalText = source.content.subSequence(from, to).toString
    
    val (sameBegin, _, distinctStart) = commonLength(originalText, text)
    
    val (sameEnd,   _, shortenedText) = commonLength(originalText.reverse, distinctStart.reverse)
        
    (shortenedText.reverse.toString, from + sameBegin, to - sameEnd)
  }
  
  def refactor(changed: TreeModifications): List[Change] = context("main refactoring") {
    
    val toplevelTrees = changed.toplevelTrees
    
    trace("%d toplevel trees have changes", toplevelTrees.size)
   
    toplevelTrees map { tree =>

      val (changedText, from, to) = createChangeText(tree, changed)
      
      Change(tree.pos.source.file, from, to, changedText) \\ (c => trace("Result: "+ c))
    }
  }
}
