/*
 * Copyright 2005-2010 LAMP/EPFL
 */

package scala.tools.refactoring
package implementations

import common.{InteractiveScalaCompiler, Change}
import transformation.TreeFactory
import scala.tools.nsc.io.AbstractFile

abstract class AddImportStatement extends Refactoring with InteractiveScalaCompiler {

  val global: tools.nsc.interactive.Global

  def addImport(file: AbstractFile, fqName: String): List[Change] = addImports(file, List(fqName))

  def addImports(file: AbstractFile, importsToAdd: Iterable[String]): List[Change] = {

    val astRoot = abstractFileToTree(file)

    refactor((addImportTransformation(importsToAdd) apply astRoot).toList)
  }
  
  @deprecated("Use addImport(file, ..) instead", "0.4.0")
  def addImport(selection: Selection, fullyQualifiedName: String): List[Change] = {
    addImport(selection.file, fullyQualifiedName)
  }
  
  @deprecated("Use addImport(file, ..) instead", "0.4.0")
  def addImport(selection: Selection, pkg: String, name: String): List[Change] = {
    addImport(selection.file, pkg +"."+ name)
  }
}
