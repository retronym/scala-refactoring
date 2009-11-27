package scala.tools.refactor.printer

import scala.tools.refactor.Tracing

import java.util.regex._

trait Merger {
  self: WhitespaceHandler with TreePrinter with Tracing =>
  
  def merge(scope: Scope, allFragments: FragmentRepository): List[Fragment] = context("merge fragments") {
    
    def withWhitespace(current: Fragment, next: Fragment, scope: Scope): List[Fragment] = {
    
      val currentExists = allFragments exists current
      
      /*if(currentExists && allFragments.getNext(current).get._3 == next) {
        val (_, wsFound, nextFound) = (allFragments getNext current).get //FIXME
        //explain("Whitespace ▒▒"+ (wsFound mkString "") +"▒▒ is between ▒▒"+ current +"▒▒ and ▒▒"+ next +"▒▒.")
        wsFound
      } else */{
        /*
         * The tree has been re-arranged, the next part in the original source isn't our current next. 
         * We have to split the whitespace between our current part and its next part in the original 
         * source
         * */
        val (whitespaceAfterCurrent, _) = 
          if(currentExists) {
            splitWhitespaceBetween(allFragments getNext current)
          } else {
            //("<current does not exist>", "<current does not exist>")
            ("", "")
          }
        
        /*
         * We also need the whitespace of our right neighbour:
         * */
        val (_, whitespaceBeforeNext) = 
          if(allFragments exists next) {
            splitWhitespaceBetween(allFragments getPrevious next)
          } else {
            //("<next does not exist>", "<next does not exist>")
            ("", "")
          }
        
        /*
         * We now have 4 fragments of whitespace, we only need the left slice of our current fragment (that is, 
         * the whitespace that is adjacent to the current fragment) and the (right) slice of whitespace that is directly
         * before the next fragment in the tree. Combined, we get all whitespace we need.
         * */
        
        /*
         * Fragments may define required strings that need to be present in the whitespace before or after them.
         * */
        
        val whitespace = processRequisites(current, whitespaceAfterCurrent, whitespaceBeforeNext, next)
        
		    trace("whitespace is %s", whitespace)
		    trace("the next fragment is %s", next)
		    
		    val existingIndentation: Option[Tuple2[Int, Int]] = allFragments.scopeIndentation(next) match {
		      case Some(s) => SourceHelper.indentationLength(next) match {
            case Some(indentation) => Some(s, indentation)
            case _ => None
          }
		      case None => None
        }
		    
		    val indentedWhitespace = fixIndentation(
          whitespace, 
          existingIndentation,
          next.isEndOfScope, 
          scope.indentation)
    
        trace("the resulting whitespace is %s", indentedWhitespace)
        
        new StringFragment(indentedWhitespace) :: Nil
      }
    }

    def innerMerge(scope: Scope): List[Fragment] = context("inner merger loop") {
      trace("current scope is %s", scope)
      (scope.children zip scope.children.tail) flatMap {
        case (current: Scope, next) => innerMerge(current) ::: withWhitespace(current, next, scope)
        case (current, next) => (current match {
          case f if allFragments exists f => f
          case f: FlagFragment => StringFragment(f.print) copyRequirements f
          case f: WithTree => print(f)
          case f: WithRequisite => StringFragment("") copyRequirements f
          case _ => StringFragment("<non-tree part>")
        }) :: withWhitespace(current, next, scope)
      }
    }
    
    innerMerge(scope)
  }
}
