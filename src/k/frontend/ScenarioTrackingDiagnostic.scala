package k.frontend

import com.microsoft.z3.{Context, BoolExpr}
import k.frontend.{IncrementalSession, SolverConfig, SolverResult, Satisfiable, Unsatisfiable, Unknown}
import scala.collection.mutable.{ListBuffer, HashMap => MMap}
import java.io.File

/**
 * Scenario tracking diagnostic using boolean variables.
 * 
 * Creates boolean variables for each scenario and uses incremental solving
 * to determine which scenarios become unsatisfiable as constraints are added.
 */
object ScenarioTrackingDiagnostic {
  
  /**
   * Represents a scenario with its boolean variable
   */
  case class Scenario(
    name: String,
    boolVar: BoolExpr,
    assumption: BoolExpr,  // The condition that defines this scenario
    description: String
  )
  
  /**
   * Result showing which scenarios are satisfiable after adding constraints
   */
  case class ScenarioStatus(
    constraintGroup: String,
    scenarioResults: Map[String, SolverResult],
    timeMs: Long
  )
  
  /**
   * Diagnose model by tracking which scenarios remain satisfiable.
   * 
   * Creates boolean variables for each scenario and checks satisfiability
   * after each constraint group to see which scenarios are eliminated.
   */
  def diagnoseWithScenarios(
    model: Model,
    smtModel: String,
    timeoutMs: Long = 30000
  ): List[ScenarioStatus] = {
    
    println("\n" + "="*70)
    println("SCENARIO TRACKING DIAGNOSTIC")
    println("="*70)
    println()
    
    val ctx = K2Z3.ctx
    val config = SolverConfig(
      timeout = Some(timeoutMs),
      produceUnsatCores = true,
      incrementalMode = true,
      verbosity = 1
    )
    
    val session = new IncrementalSession(ctx, config)
    val results = ListBuffer[ScenarioStatus]()
    
    try {
      // Ensure .tmp directory exists
      val tmpDir = new File(".tmp")
      if (!tmpDir.exists()) {
        tmpDir.mkdirs()
      }
      
      // Add scenario definitions to the SMT model before parsing
      val scenarioDefsSMT = createScenarioDefinitionsSMT()
      val smtModelWithScenarios = smtModel + "\n" + scenarioDefsSMT
      
      // Parse the full SMT model (including scenario definitions) to get all assertions
      val tempFile = new File(".tmp/k_scenario_tracking.smt2")
      val writer = new java.io.PrintWriter(tempFile)
      writer.write(smtModelWithScenarios)
      writer.close()
      
      println(s"[Diagnostic] Parsing SMT model from ${tempFile.getAbsolutePath}")
      val allAssertions = ctx.parseSMTLIB2File(
        tempFile.getAbsolutePath, Array(), Array(), Array(), Array()
      ).toList.map(_.asInstanceOf[BoolExpr])
      
      println(s"[Diagnostic] Found ${allAssertions.length} total assertions")
      println()
      
      // Create scenario boolean variables
      val scenarios = createScenarios(ctx, smtModel)
      println(s"[Diagnostic] Created ${scenarios.length} scenario(s):")
      scenarios.foreach { s =>
        println(s"  - ${s.name}: ${s.description}")
      }
      println()
      
      // Group assertions by constraint name
      val groups = IncrementalDiagnostic.groupAssertionsByConstraint(allAssertions, smtModel)
      println(s"[Diagnostic] Identified ${groups.length} constraint groups:")
      groups.foreach { g =>
        println(s"  - ${g.name}: ${g.assertions.length} assertion(s)")
      }
      println()
      
      // Add base constraints
      val baseGroup = groups.find(_.name == "Base Constraints")
      val namedGroups = groups.filter(_.name != "Base Constraints")
      
      println("[Diagnostic] Adding base constraints...")
      session.push("base")
      
      baseGroup.foreach { group =>
        group.assertions.foreach { expr =>
          session.assert(expr)
        }
      }
      
      // Scenario definitions are already in allAssertions (added to SMT model before parsing)
      // We just need to identify which assertions are scenario definitions
      println("[Diagnostic] Scenario definitions already included in parsed assertions")
      
      // Check which scenarios are satisfiable with base constraints
      val baseStatus = checkScenarioStatus(session, scenarios, "Base Constraints")
      results += baseStatus
      
      baseStatus.scenarioResults.foreach { case (name, result) =>
        result match {
          case Satisfiable(_, _) =>
            println(s"  ✓ Scenario $name: SAT")
          case Unsatisfiable(_, _) =>
            println(s"  ✗ Scenario $name: UNSAT (eliminated!)")
          case Unknown(reason, _, timeout) =>
            if (timeout) {
              println(s"  ⏱ Scenario $name: TIMEOUT")
            } else {
              println(s"  ? Scenario $name: UNKNOWN ($reason)")
            }
        }
      }
      println()
      
      // Add constraint groups incrementally and check scenario status
      for ((group, index) <- namedGroups.zipWithIndex) {
        val groupStartTime = System.currentTimeMillis()
        println(s"[Diagnostic] [${index + 1}/${namedGroups.length}] Adding constraint group: ${group.name}")
        
        session.push(group.name)
        
        // Add all assertions in this group
        group.assertions.foreach { expr =>
          session.assert(expr)
        }
        
        // Check which scenarios are still satisfiable
        val status = checkScenarioStatus(session, scenarios, group.name)
        val groupTimeMs = System.currentTimeMillis() - groupStartTime
        val statusWithTime = ScenarioStatus(status.constraintGroup, status.scenarioResults, groupTimeMs)
        results += statusWithTime
        
        // Report scenario status
        status.scenarioResults.foreach { case (name, result) =>
          result match {
            case Satisfiable(_, _) =>
              println(s"  ✓ Scenario $name: SAT")
            case Unsatisfiable(_, _) =>
              println(s"  ✗ Scenario $name: UNSAT (eliminated by ${group.name}!)")
            case Unknown(reason, _, timeout) =>
              if (timeout) {
                println(s"  ⏱ Scenario $name: TIMEOUT")
              } else {
                println(s"  ? Scenario $name: UNKNOWN ($reason)")
              }
          }
        }
        println()
        
        // Count how many scenarios are still satisfiable
        val satCount = status.scenarioResults.values.count(_.isSat)
        val unsatCount = status.scenarioResults.values.count(_.isUnsat)
        val unknownCount = status.scenarioResults.values.count(_.isUnknown)
        
        if (unsatCount == scenarios.length) {
          println(s"[Diagnostic] All scenarios eliminated! Model is unsatisfiable.")
          return results.toList
        } else if (satCount == 1 && unknownCount == 0) {
          println(s"[Diagnostic] Only one scenario remains satisfiable!")
          val remaining = status.scenarioResults.find(_._2.isSat)
          remaining.foreach { case (name, _) =>
            println(s"[Diagnostic] Remaining scenario: $name")
          }
        }
      }
      
      println("[Diagnostic] All constraint groups added")
      println()
      
    } catch {
      case e: Throwable =>
        println(s"[Diagnostic] ERROR: ${e.getMessage}")
        if (K2Z3.debug) e.printStackTrace()
    }
    
    results.toList
  }
  
  /**
   * Create scenario boolean variables for DSN_Pass.k
   * 
   * Creates boolean variables for each scenario. The scenario definitions will be
   * added as SMT assertions that reference the actual getters in the model.
   */
  private def createScenarios(ctx: Context, smtModel: String): List[Scenario] = {
    // Create scenario boolean variables
    val scenarioNominal = ctx.mkBoolConst("scenario_nominal")
    val scenarioAnomalousTolerable = ctx.mkBoolConst("scenario_anomalous_tolerable")
    val scenarioAnomalousNotTolerable = ctx.mkBoolConst("scenario_anomalous_not_tolerable")
    
    // We'll create scenario definitions as SMT strings that reference the actual getters
    // These will be parsed and added to the solver
    // The getters are: (Requirements!missedpass (Schedule!requirements (TopLevelDeclarations!schedule 0)))
    
    List(
      Scenario(
        "Nominal",
        scenarioNominal,
        ctx.mkTrue(),  // Placeholder - will be defined via SMT assertion
        "Nominal approach: requirements.missedpass = false"
      ),
      Scenario(
        "AnomalousTolerable",
        scenarioAnomalousTolerable,
        ctx.mkTrue(),  // Placeholder - will be defined via SMT assertion
        "Anomalous approach: requirements.missedpass = true && requirements.tolerate = true"
      ),
      Scenario(
        "AnomalousNotTolerable",
        scenarioAnomalousNotTolerable,
        ctx.mkTrue(),  // Placeholder - will be defined via SMT assertion
        "Anomalous approach: requirements.missedpass = true && requirements.tolerate = false"
      )
    )
  }
  
  /**
   * Create SMT assertions that define scenarios in terms of actual getters.
   * These will be parsed and added to the solver.
   */
  private def createScenarioDefinitionsSMT(): String = {
    // Declare scenario boolean variables first
    // Then create scenario definitions as SMT assertions
    // We reference the actual getters from the model
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
; Scenario 1: Nominal (missedpass = false)
(assert (= scenario_nominal (not $missedpassExpr)))

; Scenario 2: Anomalous Tolerable (missedpass = true && tolerate = true)
(assert (= scenario_anomalous_tolerable (and $missedpassExpr $tolerateExpr)))

; Scenario 3: Anomalous Not Tolerable (missedpass = true && tolerate = false)
(assert (= scenario_anomalous_not_tolerable (and $missedpassExpr (not $tolerateExpr))))
"""
  }
  
  /**
   * Check which scenarios are satisfiable given current constraints.
   * Uses assumptions to test each scenario without modifying solver state.
   */
  private def checkScenarioStatus(
    session: IncrementalSession,
    scenarios: List[Scenario],
    constraintGroup: String
  ): ScenarioStatus = {
    
    val scenarioResults = MMap[String, SolverResult]()
    
    for (scenario <- scenarios) {
      // Check if this scenario is still satisfiable using assumptions
      // We assume the scenario boolean variable is true
      val result = session.checkAssuming(scenario.boolVar)
      scenarioResults(scenario.name) = result
    }
    
    ScenarioStatus(constraintGroup, scenarioResults.toMap, 0)
  }
}

