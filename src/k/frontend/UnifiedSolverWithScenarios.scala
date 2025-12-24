package k.frontend

import com.microsoft.z3._
import k.frontend.{IncrementalSession, SolverConfig, SolverResult, Satisfiable, Unsatisfiable, Unknown}
import scala.collection.mutable.{ListBuffer, Map => MMap}

/**
 * UnifiedSolver with scenario tracking integration.
 * 
 * Uses scenario tracking to identify viable scenarios, then applies
 * unified loop solving to find solutions within those scenarios.
 */
object UnifiedSolverWithScenarios {
  
  type KModel = k.frontend.Model
  type Z3Model = com.microsoft.z3.Model
  
  /**
   * Solve model using scenario tracking + unified loop approach.
   */
  def solve(model: KModel, smtModel: String, printModel: Boolean, timeoutMs: Long = 30000): UnifiedSolver.SolveResult = {
    
    println("\n" + "="*70)
    println("UNIFIED SOLVER WITH SCENARIO TRACKING")
    println("="*70)
    println()
    
    // Step 1: Identify viable scenarios using scenario tracking
    println("[UnifiedSolver+Scenarios] Step 1: Identifying viable scenarios...")
    val scenarioStatus = ScenarioTrackingDiagnostic.diagnoseWithScenarios(model, smtModel, timeoutMs)
    
    // Find scenarios that are still SAT after all constraints
    val viableScenarios = findViableScenarios(scenarioStatus)
    
    if (viableScenarios.isEmpty) {
      println("[UnifiedSolver+Scenarios] No viable scenarios found. Model is unsatisfiable.")
      return UnifiedSolver.SolveResult.Unsat
    }
    
    println(s"[UnifiedSolver+Scenarios] Found ${viableScenarios.length} viable scenario(s):")
    viableScenarios.foreach { s =>
      println(s"  - $s")
    }
    println()
    
    // Step 2: Try to solve each viable scenario using unified loop
    for ((scenarioName, index) <- viableScenarios.zipWithIndex) {
      println(s"[UnifiedSolver+Scenarios] [${index + 1}/${viableScenarios.length}] Solving scenario: $scenarioName")
      
      val result = solveScenario(model, smtModel, scenarioName, timeoutMs)
      
      result match {
        case UnifiedSolver.SolveResult.Sat(_) =>
          println(s"[UnifiedSolver+Scenarios] ✓ Solution found in scenario: $scenarioName")
          return result
        case UnifiedSolver.SolveResult.Unsat =>
          println(s"[UnifiedSolver+Scenarios] ✗ Scenario $scenarioName is unsatisfiable")
        case UnifiedSolver.SolveResult.Timeout =>
          println(s"[UnifiedSolver+Scenarios] ⏱ Scenario $scenarioName timed out")
        case UnifiedSolver.SolveResult.Unknown(reason) =>
          println(s"[UnifiedSolver+Scenarios] ? Scenario $scenarioName: $reason")
      }
      println()
    }
    
    println("[UnifiedSolver+Scenarios] No solution found in any viable scenario")
    UnifiedSolver.SolveResult.Unknown("All viable scenarios exhausted")
  }
  
  /**
   * Find scenarios that are still satisfiable after all constraints.
   */
  private def findViableScenarios(statusList: List[ScenarioTrackingDiagnostic.ScenarioStatus]): List[String] = {
    if (statusList.isEmpty) return Nil
    
    // Get the last status (after all constraints)
    val lastStatus = statusList.last
    
    // Find scenarios that are SAT (not UNSAT or UNKNOWN)
    lastStatus.scenarioResults.filter { case (_, result) =>
      result match {
        case Satisfiable(_, _) => true
        case _ => false
      }
    }.keys.toList
  }
  
  /**
   * Solve a specific scenario using incremental solving within unified loop.
   */
  private def solveScenario(
    model: KModel,
    smtModel: String,
    scenarioName: String,
    timeoutMs: Long
  ): UnifiedSolver.SolveResult = {
    
    val ctx = K2Z3.ctx
    val config = SolverConfig(
      timeout = Some(timeoutMs),
      produceUnsatCores = true,
      incrementalMode = true,
      verbosity = 1
    )
    
    val session = new IncrementalSession(ctx, config)
    
    try {
      // Add scenario definitions to SMT
      val scenarioDefsSMT = createScenarioDefinitionsSMT()
      val smtModelWithScenarios = smtModel + "\n" + scenarioDefsSMT
      
      // Parse SMT model
      val tmpDir = new java.io.File(".tmp")
      if (!tmpDir.exists()) {
        tmpDir.mkdirs()
      }
      
      val tempFile = new java.io.File(".tmp/k_scenario_unified.smt2")
      val writer = new java.io.PrintWriter(tempFile)
      writer.write(smtModelWithScenarios)
      writer.close()
      
      val allAssertions = ctx.parseSMTLIB2File(
        tempFile.getAbsolutePath, Array(), Array(), Array(), Array()
      ).toList.map(_.asInstanceOf[BoolExpr])
      
      // Group constraints
      val groups = IncrementalDiagnostic.groupAssertionsByConstraint(allAssertions, smtModelWithScenarios)
      
      // Add base constraints
      val baseGroup = groups.find(_.name == "Base Constraints")
      val namedGroups = groups.filter(_.name != "Base Constraints")
      
      session.push("base")
      baseGroup.foreach { group =>
        group.assertions.foreach { expr =>
          session.assert(expr)
        }
      }
      
      // Add scenario assumption (assume this scenario is true)
      // Map scenario names to boolean variable names
      val scenarioVarName = scenarioName match {
        case "Nominal" => "scenario_nominal"
        case "AnomalousTolerable" => "scenario_anomalous_tolerable"
        case "AnomalousNotTolerable" => "scenario_anomalous_not_tolerable"
        case _ => s"scenario_${scenarioName.toLowerCase.replace(" ", "_")}"
      }
      val scenarioVar = ctx.mkBoolConst(scenarioVarName)
      session.push("scenario")
      session.assert(scenarioVar, Some(s"scenario_$scenarioName"))
      
      // Check base + scenario
      val baseResult = session.check()
      baseResult match {
        case Unsatisfiable(_, _) =>
          return UnifiedSolver.SolveResult.Unsat
        case Unknown(_, _, true) =>
          return UnifiedSolver.SolveResult.Timeout
        case _ =>
      }
      
      // Add constraints incrementally
      for ((group, index) <- namedGroups.zipWithIndex) {
        session.push(group.name)
        group.assertions.foreach { expr =>
          session.assert(expr)
        }
        
        val result = session.check()
        result match {
          case Satisfiable(model, _) =>
            // Continue - this group is satisfiable
          case Unsatisfiable(_, _) =>
            println(s"  ✗ UNSAT at constraint group: ${group.name}")
            return UnifiedSolver.SolveResult.Unsat
          case Unknown(_, _, true) =>
            println(s"  ⏱ TIMEOUT at constraint group: ${group.name}")
            // Return best effort if we have a model
            session.getModel match {
              case Some(model) =>
                return UnifiedSolver.SolveResult.Sat(model)
              case None =>
                return UnifiedSolver.SolveResult.Timeout
            }
          case Unknown(reason, _, _) =>
            println(s"  ? UNKNOWN at constraint group: ${group.name}: $reason")
        }
      }
      
      // All constraints added and satisfiable!
      session.getModel match {
        case Some(model) =>
          UnifiedSolver.SolveResult.Sat(model)
        case None =>
          UnifiedSolver.SolveResult.Unknown("No model available")
      }
      
    } catch {
      case e: Throwable =>
        println(s"[UnifiedSolver+Scenarios] ERROR: ${e.getMessage}")
        if (K2Z3.debug) e.printStackTrace()
        UnifiedSolver.SolveResult.Unknown(e.getMessage)
    }
  }
  
  /**
   * Create SMT assertions that define scenarios.
   */
  private def createScenarioDefinitionsSMT(): String = {
    val scheduleRef = "(TopLevelDeclarations!schedule 0)"
    val requirementsRef = s"(Schedule!requirements $scheduleRef)"
    val missedpassExpr = s"(Requirements!missedpass $requirementsRef)"
    val tolerateExpr = s"(Requirements!tolerate $requirementsRef)"
    
    s"""
; Declare scenario boolean variables
(declare-const scenario_nominal Bool)
(declare-const scenario_anomalous_tolerable Bool)
(declare-const scenario_anomalous_not_tolerable Bool)

; Scenario definitions
(assert (= scenario_nominal (not $missedpassExpr)))
(assert (= scenario_anomalous_tolerable (and $missedpassExpr $tolerateExpr)))
(assert (= scenario_anomalous_not_tolerable (and $missedpassExpr (not $tolerateExpr))))
"""
  }
  
}

