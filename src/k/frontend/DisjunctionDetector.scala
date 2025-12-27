package k.frontend

import com.microsoft.z3.BoolExpr

/**
 * General disjunction detector that identifies when constraint groups
 * naturally subdivide by disjunction (OR expressions) at the top level.
 * 
 * This allows the unified solver to try each disjunctive branch separately,
 * applying incremental constraint addition and adaptive soft constraints
 * within each branch.
 */
object DisjunctionDetector {
  
  /**
   * Represents a disjunctive branch (one branch of an OR expression)
   */
  case class DisjunctiveBranch(
    index: Int,
    expression: BoolExpr,
    description: String = ""
  )
  
  /**
   * Represents a constraint group with detected disjunctive structure
   */
  case class DisjunctiveGroup(
    groupName: String,
    originalExpression: BoolExpr,
    branches: List[DisjunctiveBranch]
  )
  
  /**
   * Detect if a constraint group has top-level disjunctive structure.
   * Returns Some(DisjunctiveGroup) if detected, None otherwise.
   * 
   * This looks for large OR expressions at the top level of a constraint group.
   * We consider it "disjunctive" if there are 2+ branches that are reasonably
   * independent (not just a small local disjunction).
   */
  def detectDisjunctiveStructure(
    groupName: String,
    assertions: List[BoolExpr]
  ): Option[DisjunctiveGroup] = {
    
    // If there's only one assertion, check if it's a top-level OR
    if (assertions.length == 1) {
      detectTopLevelOR(assertions.head, groupName).map { branches =>
        DisjunctiveGroup(groupName, assertions.head, branches)
      }
    } else {
      // Multiple assertions - check if they can be combined into a disjunction
      // For now, we only detect single-assertion disjunctions
      // Multi-assertion disjunctions could be detected by looking for
      // common OR patterns across assertions, but that's more complex
      None
    }
  }
  
  /**
   * Detect if an expression has a top-level OR with multiple branches.
   * Returns the branches if detected.
   */
  private def detectTopLevelOR(expr: BoolExpr, context: String): Option[List[DisjunctiveBranch]] = {
    val branches = extractORBranches(expr)
    
    // Only consider it a disjunction if:
    // 1. Has 2+ branches
    // 2. Not too many branches (avoid combinatorial explosion - max 10 branches)
    // 3. Each branch is reasonably complex (not just a simple literal)
    
    if (branches.length >= 2 && branches.length <= 10) {
      // Check that branches are not trivial
      val nonTrivialBranches = branches.filter { branch =>
        !isTrivial(branch)
      }
      
      if (nonTrivialBranches.length >= 2) {
        Some(nonTrivialBranches.zipWithIndex.map { case (branch, idx) =>
          DisjunctiveBranch(idx, branch, s"$context branch $idx")
        })
      } else {
        None
      }
    } else {
      None
    }
  }
  
  /**
   * Extract all branches from an OR expression (handles nested ORs).
   * If the expression is not an OR, returns a single-element list.
   */
  private def extractORBranches(expr: BoolExpr): List[BoolExpr] = {
    try {
      val funcDecl = expr.getFuncDecl
      if (funcDecl != null) {
        val funcName = funcDecl.getName.toString
        funcName match {
      case "or" =>
        // This is an OR expression - extract all arguments
        try {
          val args = expr.getArgs.map(_.asInstanceOf[BoolExpr]).toList
          // Recursively flatten nested ORs
          args.flatMap(extractORBranches)
        } catch {
          case _: Throwable => List(expr)
        }
          case _ =>
            // Not an OR - this is a single branch
            List(expr)
        }
      } else {
        // No function declaration - treat as single branch
        List(expr)
      }
    } catch {
      case _: Throwable =>
        // If we can't analyze it, treat as single branch
        List(expr)
    }
  }
  
  /**
   * Check if an expression is trivial (just a literal or very simple).
   * We want to avoid treating trivial disjunctions as meaningful branches.
   */
  private def isTrivial(expr: BoolExpr): Boolean = {
    expr.getFuncDecl.getName.toString match {
      case "true" | "false" => true
      case "not" =>
        // Check if it's just (not var) - that's still trivial
        try {
          val args = expr.getArgs
          if (args.length > 0) {
            args(0).asInstanceOf[BoolExpr].getFuncDecl.getName.toString != "or"
          } else {
            false
          }
        } catch {
          case _: Throwable => false
        }
      case _ =>
        // Check if it's just a variable reference
        expr.getNumArgs == 0 && expr.isConst
    }
  }
}

