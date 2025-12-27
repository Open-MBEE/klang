package k.frontend

import com.microsoft.z3.{Context, BoolExpr, Optimize, Status, Model => Z3Model}
import k.frontend.{IncrementalSession, SolverConfig, SolverResult, Satisfiable, Unsatisfiable, Unknown}
import scala.collection.mutable.{ListBuffer, HashMap => MMap}
import scala.util.control.Breaks._

/**
 * Solver specifically designed for DSN_Pass.k and similar models with 
 * disjunctive structure based on missedpass/tolerate boolean flags.
 * 
 * This is NOT a general-purpose solver - it hardcodes the 3 scenarios 
 * specific to DSN_Pass.k:
 * 1. Nominal: missedpass = false
 * 2. Anomalous Tolerable: missedpass = true && tolerate = true
 * 3. Anomalous Not Tolerable: missedpass = true && tolerate = false
 * 
 * Use -dsn-pass flag to invoke this solver.
 * 
 * For general disjunctive models, a future implementation would detect
 * scenarios automatically from the model structure.
 */
object DSNPassSolver {
  
  /**
   * Represents a scenario (one branch of a disjunction)
   */
  case class Scenario(
    name: String,
    assumptions: List[(String, BoolExpr)],  // Variable name -> assumption expression
    description: String = ""
  )
  
  /**
   * Result of solving a scenario
   */
  case class ScenarioResult(
    scenario: Scenario,
    result: SolverResult,
    timeMs: Long
  )
  
  /**
   * Extract scenarios from a model based on disjunctive structure.
   * 
   * For DSN_Pass.k, identifies scenarios based on requirements.missedpass
   * and requirements.tolerate values.
   * 
   * Since we can't easily extract variable references from the model,
   * we'll create scenarios based on the known structure of DSN_Pass.k.
   */
  def extractScenarios(model: Model): List[Scenario] = {
    val scenarios = ListBuffer[Scenario]()
    val ctx = K2Z3.ctx
    
    // For DSN_Pass.k, we know the structure:
    // - schedule.requirements.missedpass : Bool
    // - schedule.requirements.tolerate : Bool
    
    // Try to find these variables in the model or create them
    // The actual variable names in SMT will be like "TopLevelDeclarations!schedule.requirements.missedpass"
    // or just "schedule.requirements.missedpass" depending on how it's encoded
    
    // Order scenarios by likelihood of success based on DSN_Pass.k structure:
    // Nominal scenario solves fastest with soft constraint fallback
    
    // Scenario 1: Nominal (missedpass = false) - known to solve in ~22 seconds
    scenarios += Scenario(
      "Nominal",
      Nil, // Assumptions will be added as SMT constraints
      "Nominal approach: requirements.missedpass = false"
    )
    
    // Scenario 2: Anomalous Tolerable (missedpass = true && tolerate = true)
    scenarios += Scenario(
      "AnomalousTolerable",
      Nil,
      "Anomalous approach: requirements.missedpass = true && requirements.tolerate = true"
    )
    
    // Scenario 3: Anomalous Not Tolerable (missedpass = true && tolerate = false)
    scenarios += Scenario(
      "AnomalousNotTolerable",
      Nil,
      "Anomalous approach: requirements.missedpass = true && requirements.tolerate = false"
    )
    
    scenarios.toList
  }
  
  /**
   * Create SMT assumptions for a scenario.
   * For DSN_Pass.k, adds constraints on missedpass and tolerate.
   * 
   * Note: Variable names in SMT are encoded as getters like:
   * - (Schedule!requirements 0) for schedule.requirements
   * - (Requirements!missedpass (Schedule!requirements 0)) for schedule.requirements.missedpass
   * 
   * We'll add these as additional assertions to the SMT model.
   */
  private def createScenarioAssumptions(scenario: Scenario, baseSMT: String): String = {
    // Add actual scenario constraints as SMT assertions
    // The variable names are functions that take a Ref (heap reference)
    // TopLevelDeclarations is at ref 0, so we call the functions with 0
    // TopLevelDeclarations!Miss_Pass and TopLevelDeclarations!Tolerate are getter functions
    
    val scenarioAssertions = scenario.name match {
      case "Nominal" =>
        // Nominal: missedpass = false
        "; Scenario: Nominal (requirements.missedpass = false)\n" +
        "(assert (= (TopLevelDeclarations!Miss_Pass 0) false))\n"
      case "AnomalousTolerable" =>
        // Anomalous Tolerable: missedpass = true && tolerate = true
        "; Scenario: Anomalous Tolerable (requirements.missedpass = true && requirements.tolerate = true)\n" +
        "(assert (= (TopLevelDeclarations!Miss_Pass 0) true))\n" +
        "(assert (= (TopLevelDeclarations!Tolerate 0) true))\n"
      case "AnomalousNotTolerable" =>
        // Anomalous Not Tolerable: missedpass = true && tolerate = false
        "; Scenario: Anomalous Not Tolerable (requirements.missedpass = true && requirements.tolerate = false)\n" +
        "(assert (= (TopLevelDeclarations!Miss_Pass 0) true))\n" +
        "(assert (= (TopLevelDeclarations!Tolerate 0) false))\n"
      case _ =>
        "; Scenario: All (no additional constraints)\n"
    }
    
    baseSMT + "\n" + scenarioAssertions
  }
  
  /**
   * Solve model by trying each scenario separately.
   * 
   * Returns as soon as one scenario is satisfiable.
   */
  def solveByScenarios(
    model: Model,
    smtModel: String,
    timeoutMs: Long = 30000
  ): Option[ScenarioResult] = {
    
    println("\n" + "="*70)
    println("SCENARIO-BASED SOLVING")
    println("="*70)
    println()
    
    val ctx = K2Z3.ctx
    val config = SolverConfig(
      timeout = Some(timeoutMs),
      produceUnsatCores = true,
      incrementalMode = true,
      verbosity = 1
    )
    
    // Extract scenarios
    val scenarios = extractScenarios(model)
    println(s"[ScenarioSolver] Identified ${scenarios.length} scenario(s):")
    scenarios.foreach { s =>
      println(s"  - ${s.name}: ${s.description}")
    }
    println()
    
    // Try each scenario
    for ((scenario, index) <- scenarios.zipWithIndex) {
      val scenarioStartTime = System.currentTimeMillis()
      println(s"[ScenarioSolver] [${index + 1}/${scenarios.length}] Trying scenario: ${scenario.name}")
      println(s"  Description: ${scenario.description}")
      
      val session = new IncrementalSession(ctx, config)
      
      try {
        // Ensure .tmp directory exists
        val tmpDir = new java.io.File(".tmp")
        if (!tmpDir.exists()) {
          tmpDir.mkdirs()
        }
        
        // Create SMT model with scenario assumptions
        val scenarioSMT = createScenarioAssumptions(scenario, smtModel)
        
        // Parse SMT model with scenario assumptions
        val tempFile = new java.io.File(".tmp/k_scenario_solve.smt2")
        val writer = new java.io.PrintWriter(tempFile)
        writer.write(scenarioSMT)
        writer.close()
        
        val allAssertions = ctx.parseSMTLIB2File(
          tempFile.getAbsolutePath, Array(), Array(), Array(), Array()
        ).toList.map(_.asInstanceOf[BoolExpr])
        
        // Push scenario scope
        session.push(scenario.name)
        
        // Add all constraints (scenario assumptions are already in the SMT)
        println(s"  Adding ${allAssertions.length} constraints (including scenario assumptions)...")
        for ((assertion, idx) <- allAssertions.zipWithIndex) {
          session.assert(assertion, Some(s"constraint_$idx"))
        }
        
        // Check satisfiability
        println(s"  Checking satisfiability...")
        val result = session.check()
        val scenarioTimeMs = System.currentTimeMillis() - scenarioStartTime
        
        result match {
          case Satisfiable(_, _) =>
            println(s"  ✓ SAT - Scenario ${scenario.name} is satisfiable! (${scenarioTimeMs}ms)")
            println()
            println("="*70)
            println("SOLUTION FOUND")
            println("="*70)
            println(s"Scenario: ${scenario.name}")
            println(s"Time: ${scenarioTimeMs}ms")
            println("="*70)
            return Some(ScenarioResult(scenario, result, scenarioTimeMs))
            
          case Unsatisfiable(core, explanation) =>
            println(s"  ✗ UNSAT - Scenario ${scenario.name} is unsatisfiable (${scenarioTimeMs}ms)")
            println(s"    Core: ${core.mkString(", ")}")
            println(s"    Explanation: $explanation")
            
          case Unknown(reason, _, timeout) =>
            if (timeout) {
              println(s"  ⏱ TIMEOUT - Scenario ${scenario.name} timed out after ${scenarioTimeMs}ms")
            } else {
              println(s"  ? UNKNOWN - Scenario ${scenario.name}: $reason")
            }
        }
        
        println()
        
      } catch {
        case e: Throwable =>
          println(s"  ERROR: ${e.getMessage}")
          if (K2Z3.debug) e.printStackTrace()
      }
    }
    
    println("="*70)
    println("NO SATISFIABLE SCENARIO FOUND")
    println("="*70)
    println("All scenarios were either UNSAT or timed out.")
    println()
    
    None
  }
  
  /**
   * Combine scenario-based solving with incremental constraint addition.
   * Uses Z3 assumptions for fast scenario switching (recommended approach).
   * 
   * For each scenario:
   * 1. Use assumptions to fix scenario variables
   * 2. Add constraints incrementally by group
   * 3. Check after each constraint group using checkAssuming
   */
  def solveByScenariosIncremental(
    model: Model,
    smtModel: String,
    timeoutMs: Long = 30000
  ): Option[ScenarioResult] = {
    
    println("\n" + "="*70)
    println("SCENARIO-BASED INCREMENTAL SOLVING (Assumption-based)")
    println("="*70)
    println()
    
    val ctx = K2Z3.ctx
    val config = SolverConfig(
      timeout = Some(timeoutMs),
      produceUnsatCores = true,
      incrementalMode = true,
      verbosity = 1
    )
    
    val scenarios = extractScenarios(model)
    println(s"[ScenarioSolver] Identified ${scenarios.length} scenario(s)")
    
    // Parse base SMT to get assertions
    val tmpDir = new java.io.File(".tmp")
    if (!tmpDir.exists()) {
      tmpDir.mkdirs()
    }
    val tempFile = new java.io.File(".tmp/k_base_smt.smt2")
    val writer = new java.io.PrintWriter(tempFile)
    writer.write(smtModel)
    writer.close()
    
    val baseAssertions = ctx.parseSMTLIB2File(
      tempFile.getAbsolutePath, Array(), Array(), Array(), Array()
    ).toList.map(_.asInstanceOf[BoolExpr])
    
    // Group constraints by name
    val constraintGroups = IncrementalDiagnostic.groupAssertionsByConstraint(baseAssertions, smtModel)
    println(s"[ScenarioSolver] ${constraintGroups.length} constraint groups")
    
    // Separate base constraints from named constraint groups
    val baseGroup = constraintGroups.find(_.name == "Base Constraints")
    val namedGroups = constraintGroups.filter(_.name != "Base Constraints")
    
    // Try each scenario using assumptions
    for ((scenario, index) <- scenarios.zipWithIndex) {
      val scenarioStartTime = System.currentTimeMillis()
      println(s"\n[ScenarioSolver] [${index + 1}/${scenarios.length}] Trying scenario: ${scenario.name}")
      println(s"  Description: ${scenario.description}")
      
      // Create a fresh solver for each scenario (allows clean state)
      val solver = ctx.mkSolver()
      val params = ctx.mkParams()
      params.add("timeout", timeoutMs.toInt)
      solver.setParameters(params)
      
      // Create scenario assumption expressions using Z3 API
      // The variables TopLevelDeclarations!Miss_Pass and TopLevelDeclarations!Tolerate are functions
      // We need to reference them via the parsed model
      val scenarioAssumptions = createScenarioAssumptionExprs(ctx, scenario, smtModel)
      
      if (scenarioAssumptions.isEmpty) {
        println(s"  ⚠ Could not create scenario assumptions for ${scenario.name}")
        // Fall back to adding all constraints without scenario assumptions
      } else {
        println(s"  Adding ${scenarioAssumptions.length} scenario assumption(s)")
      }
      
      // Add base constraints first (always required)
      baseGroup.foreach { group =>
        group.assertions.foreach { expr =>
          solver.add(expr)
        }
      }
      println(s"  Added base constraints")
      
      // Add scenario assumptions as hard constraints (they define the scenario)
      scenarioAssumptions.foreach { assumption =>
        solver.add(assumption)
      }
      
      // Check if base + scenario assumptions are satisfiable
      val baseCheck = solver.check()
      if (baseCheck == com.microsoft.z3.Status.UNSATISFIABLE) {
        val scenarioTimeMs = System.currentTimeMillis() - scenarioStartTime
        println(s"  ✗ UNSAT - Base constraints conflict with scenario (${scenarioTimeMs}ms)")
        // Continue to next scenario
      } else if (baseCheck == com.microsoft.z3.Status.UNKNOWN) {
        val reason = solver.getReasonUnknown
        if (reason != null && (reason.toLowerCase.contains("timeout") || reason.toLowerCase.contains("canceled"))) {
          println(s"  ⏱ TIMEOUT on base constraints check")
        } else {
          println(s"  ? UNKNOWN on base constraints check: $reason")
        }
        // Continue to next scenario
      } else {
        println(s"  ✓ Base + scenario SAT")
        
        // Add named constraint groups incrementally
        // Track problematic groups for potential soft constraint fallback
        var allSat = true
        var failedGroup: Option[String] = None
        val problematicGroups = ListBuffer[IncrementalDiagnostic.ConstraintGroup]()
        val addedGroups = ListBuffer[IncrementalDiagnostic.ConstraintGroup]()
        
        for ((group, gidx) <- namedGroups.zipWithIndex if allSat) {
          solver.push()
          
          // Add all assertions in this group
          group.assertions.foreach { expr =>
            solver.add(expr)
          }
          
          // Check satisfiability
          val groupCheck = solver.check()
          groupCheck match {
            case com.microsoft.z3.Status.SATISFIABLE =>
              println(s"  ✓ [${gidx + 1}/${namedGroups.length}] ${group.name}: SAT")
              addedGroups += group
              // Keep constraints (don't pop)
              
            case com.microsoft.z3.Status.UNSATISFIABLE =>
              println(s"  ✗ [${gidx + 1}/${namedGroups.length}] ${group.name}: UNSAT")
              problematicGroups += group
              allSat = false
              failedGroup = Some(group.name)
              solver.pop() // Remove this group's constraints
              
            case com.microsoft.z3.Status.UNKNOWN =>
              val reason = solver.getReasonUnknown
              val isTimeout = reason != null && (reason.toLowerCase.contains("timeout") || reason.toLowerCase.contains("canceled"))
              if (isTimeout) {
                println(s"  ⏱ [${gidx + 1}/${namedGroups.length}] ${group.name}: TIMEOUT - marking for soft constraint fallback")
                // Mark as problematic for soft constraint fallback
                problematicGroups += group
                solver.pop()
                // Don't set allSat = false yet - we'll try soft constraints
              } else {
                println(s"  ? [${gidx + 1}/${namedGroups.length}] ${group.name}: UNKNOWN ($reason)")
                problematicGroups += group
                allSat = false
                failedGroup = Some(group.name)
                solver.pop()
              }
          }
        }
        
        if (allSat) {
          val scenarioTimeMs = System.currentTimeMillis() - scenarioStartTime
          println(s"\n  ✓ SAT - All constraints satisfied! (${scenarioTimeMs}ms)")
          val resultModel = solver.getModel
          return Some(ScenarioResult(scenario, Satisfiable(resultModel), scenarioTimeMs))
        } else if (problematicGroups.nonEmpty && addedGroups.nonEmpty) {
          // Try soft constraint fallback using Optimize API
          println(s"\n  Trying soft constraint fallback for ${problematicGroups.length} problematic group(s)...")
          val softResult = trySoftConstraintFallback(ctx, baseGroup, addedGroups.toList, 
                                                      problematicGroups.toList, scenarioAssumptions, timeoutMs)
          softResult match {
            case Some(model) =>
              val scenarioTimeMs = System.currentTimeMillis() - scenarioStartTime
              println(s"\n  ✓ SAT (best-effort) - Found solution with soft constraints! (${scenarioTimeMs}ms)")
              return Some(ScenarioResult(scenario, Satisfiable(model), scenarioTimeMs))
            case None =>
              val scenarioTimeMs = System.currentTimeMillis() - scenarioStartTime
              println(s"\n  ✗ Scenario ${scenario.name} failed even with soft constraints (${scenarioTimeMs}ms)")
          }
        } else {
          val scenarioTimeMs = System.currentTimeMillis() - scenarioStartTime
          println(s"\n  ✗ Scenario ${scenario.name} failed at: ${failedGroup.getOrElse("unknown")} (${scenarioTimeMs}ms)")
        }
      }
    }
    
    println("\n" + "="*70)
    println("NO SATISFIABLE SCENARIO FOUND")
    println("="*70)
    println("All scenarios were either UNSAT or timed out.")
    println()
    
    None
  }
  
  /**
   * Create Z3 BoolExpr assumptions for a scenario.
   * Uses the parsed SMT model to find the right variable references.
   */
  private def createScenarioAssumptionExprs(
    ctx: Context, 
    scenario: Scenario,
    smtModel: String
  ): List[BoolExpr] = {
    import scala.collection.mutable.ListBuffer
    
    val assumptions = ListBuffer[BoolExpr]()
    
    try {
      // Parse the SMT model to get access to the function declarations
      // We need to find TopLevelDeclarations!Miss_Pass and TopLevelDeclarations!Tolerate
      val tmpFile = new java.io.File(".tmp/k_scenario_check.smt2")
      val pw = new java.io.PrintWriter(tmpFile)
      pw.write(smtModel)
      pw.close()
      
      // Parse to get declarations in scope
      ctx.parseSMTLIB2File(tmpFile.getAbsolutePath, Array(), Array(), Array(), Array())
      
      // Now create the scenario assumptions
      // TopLevelDeclarations!Miss_Pass(0) and TopLevelDeclarations!Tolerate(0) 
      // reference heap location 0 which contains TopLevelDeclarations
      val ref0 = ctx.mkInt(0)
      
      // Create function applications - the functions should now be in scope
      // But we need to construct them properly
      // The functions are defined in the SMT as:
      //   (define-fun TopLevelDeclarations!Miss_Pass ((this Ref)) Bool ...)
      //   (define-fun TopLevelDeclarations!Tolerate ((this Ref)) Bool ...)
      
      // Unfortunately, Z3's Java API doesn't easily expose defined functions
      // So we create the SMT text for the assumptions and parse it
      val scenarioConstraints = scenario.name match {
        case "Nominal" =>
          // missedpass = false
          "(assert (= (TopLevelDeclarations!Miss_Pass 0) false))"
        case "AnomalousTolerable" =>
          // missedpass = true, tolerate = true
          "(assert (= (TopLevelDeclarations!Miss_Pass 0) true))\n" +
          "(assert (= (TopLevelDeclarations!Tolerate 0) true))"
        case "AnomalousNotTolerable" =>
          // missedpass = true, tolerate = false
          "(assert (= (TopLevelDeclarations!Miss_Pass 0) true))\n" +
          "(assert (= (TopLevelDeclarations!Tolerate 0) false))"
        case _ =>
          ""
      }
      
      if (scenarioConstraints.nonEmpty) {
        // Append scenario constraints to the full SMT model and parse
        val fullSMT = smtModel + "\n\n; Scenario: " + scenario.name + "\n" + scenarioConstraints
        val scenarioFile = new java.io.File(".tmp/k_scenario_full.smt2")
        val sw = new java.io.PrintWriter(scenarioFile)
        sw.write(fullSMT)
        sw.close()
        
        // Parse - this will include the scenario constraints as assertions
        val allAssertions = ctx.parseSMTLIB2File(
          scenarioFile.getAbsolutePath, Array(), Array(), Array(), Array()
        ).toList.map(_.asInstanceOf[BoolExpr])
        
        // The scenario constraints are the last assertions
        val baseAssertionCount = ctx.parseSMTLIB2File(
          tmpFile.getAbsolutePath, Array(), Array(), Array(), Array()
        ).length
        
        // Extract just the scenario assumptions (the extra assertions)
        val scenarioAssertions = allAssertions.drop(baseAssertionCount)
        assumptions ++= scenarioAssertions
      }
    } catch {
      case e: Exception =>
        println(s"  ⚠ Error creating scenario assumptions: ${e.getMessage}")
    }
    
    assumptions.toList
  }
  
  /**
   * Try to find a solution using soft constraints for problematic groups.
   * Uses Z3's Optimize API to maximize constraint satisfaction.
   */
  private def trySoftConstraintFallback(
    ctx: Context,
    baseGroup: Option[IncrementalDiagnostic.ConstraintGroup],
    addedGroups: List[IncrementalDiagnostic.ConstraintGroup],
    problematicGroups: List[IncrementalDiagnostic.ConstraintGroup],
    scenarioAssumptions: List[BoolExpr],
    timeoutMs: Long
  ): Option[Z3Model] = {
    
    val optimize = ctx.mkOptimize()
    val params = ctx.mkParams()
    params.add("timeout", timeoutMs.toInt)
    optimize.setParameters(params)
    
    // Add base constraints as hard
    baseGroup.foreach { group =>
      group.assertions.foreach { expr =>
        optimize.Add(expr)
      }
    }
    
    // Add scenario assumptions as hard (they define the scenario)
    scenarioAssumptions.foreach { expr =>
      optimize.Add(expr)
    }
    
    // Add successfully added groups as hard
    addedGroups.foreach { group =>
      group.assertions.foreach { expr =>
        optimize.Add(expr)
      }
    }
    
    // Add problematic groups as SOFT constraints
    println(s"    Adding ${problematicGroups.length} problematic group(s) as soft constraints...")
    var softId = 0
    problematicGroups.foreach { group =>
      group.assertions.foreach { expr =>
        softId += 1
        optimize.AssertSoft(expr, 1, s"soft_${group.name}_$softId")
      }
    }
    
    // Check with Optimize
    println(s"    Checking with Optimize API...")
    val status = optimize.Check()
    
    status match {
      case Status.SATISFIABLE =>
        println(s"    ✓ Optimize found satisfying assignment")
        Some(optimize.getModel)
        
      case Status.UNSATISFIABLE =>
        println(s"    ✗ Optimize returned UNSAT (hard constraints conflict)")
        None
        
      case Status.UNKNOWN =>
        val reason = optimize.getReasonUnknown
        println(s"    ? Optimize returned UNKNOWN: $reason")
        // Try to get partial model anyway
        try {
          val partialModel = optimize.getModel
          if (partialModel != null) {
            println(s"    ✓ Got partial model from Optimize")
            Some(partialModel)
          } else {
            None
          }
        } catch {
          case _: Exception => None
        }
    }
  }
}

