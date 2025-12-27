package k.frontend

import com.microsoft.z3.{Context, Status, BoolExpr}
import k.frontend.{IncrementalSession, SolverConfig, SolverResult, Satisfiable, Unsatisfiable, Unknown}
import scala.collection.mutable.{ListBuffer, HashMap => MMap}
import java.io.File

/**
 * Incremental diagnostic tool for identifying problematic constraint groups.
 * 
 * This tool uses incremental solving to add constraint groups one at a time,
 * helping identify which group causes timeouts or unsatisfiability.
 */
object IncrementalDiagnostic {
  
  /**
   * Represents a group of constraints that should be added together
   */
  case class ConstraintGroup(
    name: String,
    assertions: List[BoolExpr],
    assertionLabels: List[String]  // Z3 assertion labels for unsat core tracking
  )
  
  /**
   * Diagnostic result for a constraint group
   */
  case class GroupResult(
    groupName: String,
    result: SolverResult,
    timeMs: Long
  )
  
  /**
   * Diagnose a model by adding constraint groups incrementally.
   * 
   * @param model The K model
   * @param smtModel The full SMT-LIB2 model as a string
   * @param timeoutMs Timeout per check (default 30 seconds)
   * @return List of results for each constraint group
   */
  def diagnoseModel(
    model: Model,
    smtModel: String,
    timeoutMs: Long = 30000
  ): List[GroupResult] = {
    
    println("\n" + "="*70)
    println("INCREMENTAL SOLVING DIAGNOSTIC")
    println("="*70)
    println()
    
    // Reuse existing Z3 context
    val ctx = K2Z3.ctx
    
    // Configure incremental session
    val config = SolverConfig(
      timeout = Some(timeoutMs),
      produceUnsatCores = true,
      incrementalMode = true,
      verbosity = 1
    )
    
    val session = new IncrementalSession(ctx, config)
    val results = ListBuffer[GroupResult]()
    
    try {
      // Ensure .tmp directory exists
      val tmpDir = new File(".tmp")
      if (!tmpDir.exists()) {
        tmpDir.mkdirs()
      }
      
      // Parse the full SMT model to get all assertions
      val tempFile = new File(".tmp/k_incremental_diagnostic.smt2")
      val writer = new java.io.PrintWriter(tempFile)
      writer.write(smtModel)
      writer.close()
      
      println(s"[Diagnostic] Parsing SMT model from ${tempFile.getAbsolutePath}")
      val allAssertions = ctx.parseSMTLIB2File(
        tempFile.getAbsolutePath, Array(), Array(), Array(), Array()
      ).toList.map(_.asInstanceOf[BoolExpr])
      
      println(s"[Diagnostic] Found ${allAssertions.length} total assertions")
      println()
      
      // Group assertions by constraint name using the constraint mapping
      val groups = groupAssertionsByConstraint(allAssertions, smtModel)
      
      println(s"[Diagnostic] Identified ${groups.length} constraint groups:")
      groups.foreach { g =>
        println(s"  - ${g.name}: ${g.assertions.length} assertion(s)")
      }
      println()
      
      // Add base constraints first (class invariants, type constraints, etc.)
      // These are constraints without named groups
      val baseGroup = groups.find(_.name == "Base Constraints")
      val namedGroups = groups.filter(_.name != "Base Constraints")
      
      println("[Diagnostic] Adding base constraints...")
      session.push("base")
      
      baseGroup.foreach { group =>
        group.assertions.foreach { expr =>
          session.assert(expr)
        }
      }
      
      val baseResult = session.check()
      val baseTime = System.currentTimeMillis()
      val baseTimeMs = 0L // Base check time
      
      baseResult match {
        case Satisfiable(_, _) =>
          println(s"  ✓ Base constraints are satisfiable")
        case Unsatisfiable(core, explanation) =>
          println(s"  ✗ Base constraints are unsatisfiable!")
          println(s"    Core: ${core.mkString(", ")}")
          println(s"    Explanation: $explanation")
          results += GroupResult("Base Constraints", baseResult, baseTimeMs)
          return results.toList  // Can't proceed if base is UNSAT
        case Unknown(reason, _, timeout) =>
          if (timeout) {
            println(s"  ⏱ Base constraints cause timeout!")
          } else {
            println(s"  ? Base constraints: $reason")
          }
          results += GroupResult("Base Constraints", baseResult, baseTimeMs)
          return results.toList
      }
      println()
      
      // Add constraint groups incrementally
      for ((group, index) <- namedGroups.zipWithIndex) {
        val groupStartTime = System.currentTimeMillis()
        println(s"[Diagnostic] [${index + 1}/${namedGroups.length}] Adding constraint group: ${group.name}")
        
        session.push(group.name)
        
        // Add all assertions in this group
        group.assertions.foreach { expr =>
          session.assert(expr)
        }
        
        // Check satisfiability
        val result = session.check()
        val groupTimeMs = System.currentTimeMillis() - groupStartTime
        
        result match {
          case Satisfiable(_, _) =>
            println(s"  ✓ SAT - ${group.name} is satisfiable (${groupTimeMs}ms)")
          case Unsatisfiable(core, explanation) =>
            println(s"  ✗ UNSAT - ${group.name} makes constraints unsatisfiable!")
            println(s"    Core: ${core.mkString(", ")}")
            println(s"    Explanation: $explanation")
          case Unknown(reason, _, timeout) =>
            if (timeout) {
              println(s"  ⏱ TIMEOUT - ${group.name} causes timeout after ${groupTimeMs}ms!")
              println(s"    This is likely the problematic constraint group.")
            } else {
              println(s"  ? UNKNOWN - ${group.name}: $reason")
            }
        }
        
        results += GroupResult(group.name, result, groupTimeMs)
        println()
        
        // If we got UNSAT, stop early
        result match {
          case Unsatisfiable(_, _) =>
            println(s"[Diagnostic] Stopping early due to UNSAT in ${group.name}")
            return results.toList
          case Unknown(_, _, true) =>
            // Timeout - log but continue to see if we can identify more issues
            println(s"[Diagnostic] ⚠️  Timeout in ${group.name} - continuing to check remaining groups...")
          case _ => // Continue
        }
      }
      
      println("[Diagnostic] All constraint groups added successfully")
      println()
      
    } catch {
      case e: Throwable =>
        println(s"[Diagnostic] ERROR: ${e.getMessage}")
        if (K2Z3.debug) e.printStackTrace()
    }
    
    results.toList
  }
  
  /**
   * Group assertions by their constraint names using the constraint mapping.
   * 
   * This version parses the SMT model to extract assertion labels and maps
   * them to constraint names via UtilSMT.constraintMessageMap.
   */
  def groupAssertionsByConstraint(
    assertions: List[BoolExpr],
    smtModel: String
  ): List[ConstraintGroup] = {
    
    // Extract assertion labels from SMT model
    // Pattern: (assert ... :named _xkassertN) or (assert-soft ... :id _xksoftN)
    val assertLabelPattern = """:named\s+(_xkassert\d+)|:id\s+(_xksoft\d+)""".r
    val labelToIndex = MMap[String, Int]()
    
    // Find all assertion start positions first
    val assertStartPattern = "(?m)^\\s*\\(assert\\b".r
    val assertStarts = assertStartPattern.findAllMatchIn(smtModel).map(_.start).toList
    
    // Find all assertion labels and their positions in the SMT string
    for (m <- assertLabelPattern.findAllMatchIn(smtModel)) {
      val label = if (m.group(1) != null) m.group(1) else m.group(2)
      val labelPos = m.start
      
      // Find which assertion contains this label
      // The label belongs to the LAST assertion that starts before the label position
      val containingAssertIndex = assertStarts.zipWithIndex
        .takeWhile(_._1 < labelPos)
        .lastOption
        .map(_._2)
        .getOrElse(-1)
      
      if (containingAssertIndex >= 0 && containingAssertIndex < assertions.length) {
        labelToIndex(label) = containingAssertIndex
      }
    }
    
    // Group assertions by constraint name
    val groupsByName = MMap[String, ListBuffer[Int]]()  // constraint name -> assertion indices
    val ungroupedIndices = ListBuffer[Int]()
    
    // Map labels to constraint names
    for ((label, index) <- labelToIndex) {
      val constraintDesc = UtilSMT.constraintMessageMap.getOrElse(label, "")
      
      // Extract constraint name from description
      // Format: "Nominal_Anomaly_Impact: ..." or "req Nominal_Anomaly_Impact: ..."
      val constraintName = extractConstraintName(constraintDesc)
      
      if (constraintName.isDefined) {
        val name = constraintName.get
        if (!groupsByName.contains(name)) {
          groupsByName(name) = ListBuffer()
        }
        groupsByName(name) += index
      } else if (!constraintDesc.contains("_k_ignore_")) {
        // Not a named constraint, but not ignored - put in "Other Constraints"
        if (!groupsByName.contains("Other Constraints")) {
          groupsByName("Other Constraints") = ListBuffer()
        }
        groupsByName("Other Constraints") += index
      } else {
        // Ignored constraints (type checks, etc.) - put in "Base Constraints"
        if (!groupsByName.contains("Base Constraints")) {
          groupsByName("Base Constraints") = ListBuffer()
        }
        groupsByName("Base Constraints") += index
      }
    }
    
    // Add any assertions that weren't labeled
    for (i <- assertions.indices) {
      if (!labelToIndex.values.toSet.contains(i)) {
        ungroupedIndices += i
      }
    }
    
    // Debug: Check if heap initialization is in ungrouped
    val heapInitIndex = assertions.indexWhere { expr =>
      val str = expr.toString
      str.contains("heap") && str.contains("store") && str.contains("=") && 
      str.contains("lift-TopLevelDeclarations") && str.contains("const-0")
    }
    if (heapInitIndex >= 0) {
      val isUngrouped = ungroupedIndices.contains(heapInitIndex)
      val isInLabelToIndex = labelToIndex.values.toSet.contains(heapInitIndex)
      if (K2Z3.debug) {
        println(s"[groupAssertionsByConstraint] Heap init at index $heapInitIndex: isUngrouped=$isUngrouped, isInLabelToIndex=$isInLabelToIndex")
        // Find which label is mapped to this index
        labelToIndex.foreach { case (label, idx) =>
          if (idx == heapInitIndex) {
            println(s"  ERROR: Heap init (index $idx) is incorrectly mapped to label '$label'")
            val constraintDesc = UtilSMT.constraintMessageMap.getOrElse(label, "")
            println(s"    Label maps to constraint description: '$constraintDesc'")
          }
        }
      }
    }
    
    if (ungroupedIndices.nonEmpty) {
      if (!groupsByName.contains("Base Constraints")) {
        groupsByName("Base Constraints") = ListBuffer()
      }
      groupsByName("Base Constraints") ++= ungroupedIndices
    }
    
    // Build constraint groups
    val groups = ListBuffer[ConstraintGroup]()
    
    // Add base constraints first
    groupsByName.get("Base Constraints").foreach { indices =>
      val baseAssertions = indices.map(assertions(_)).toList
      if (baseAssertions.nonEmpty) {
        groups += ConstraintGroup("Base Constraints", baseAssertions, indices.map(i => s"_assertion_$i").toList)
      }
    }
    
    // Add named constraint groups
    for ((name, indices) <- groupsByName if name != "Base Constraints") {
      val groupAssertions = indices.map(assertions(_)).toList
      val labels = indices.flatMap { idx =>
        labelToIndex.find(_._2 == idx).map(_._1)
      }.toList
      groups += ConstraintGroup(name, groupAssertions, labels)
    }
    
    groups.toList
  }
  
  /**
   * Extract constraint name from constraint description.
   * Handles formats like:
   * - "Nominal_Anomaly_Impact: ..."
   * - "req Nominal_Anomaly_Impact: ..."
   * - "AutonomousManeauver: ..."
   */
  private def extractConstraintName(desc: String): Option[String] = {
    if (desc.isEmpty) return None
    
    // Known constraint names from DSN_Pass.k
    val knownNames = List(
      "Nominal_Anomaly_Impact",
      "AutonomousManeauver",
      "AvailManeauver",
      "DopplerAfterApproachOTM",
      "ProvideCarrier",
      "CoverageOTMS",
      "OTM_Pass_Timing",
      "Valid"
    )
    
    // Check if description contains any known constraint name
    for (name <- knownNames) {
      if (desc.contains(name)) {
        return Some(name)
      }
    }
    
    // Try to extract from "req Name:" pattern
    val reqPattern = """req\s+(\w+):""".r
    reqPattern.findFirstMatchIn(desc).foreach { m =>
      return Some(m.group(1))
    }
    
    // Try to extract from "Name:" pattern at start
    val namePattern = """^(\w+):""".r
    namePattern.findFirstMatchIn(desc.trim).foreach { m =>
      return Some(m.group(1))
    }
    
    None
  }
  
  /**
   * Simplified version that adds constraints in chunks based on SMT structure.
   * This version parses the SMT file and groups assertions by sections.
   */
  def diagnoseModelBySections(
    model: Model,
    smtModel: String,
    timeoutMs: Long = 30000
  ): List[GroupResult] = {
    
    println("\n" + "="*70)
    println("INCREMENTAL SOLVING DIAGNOSTIC (By Sections)")
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
    val results = ListBuffer[GroupResult]()
    
    try {
      // Parse SMT model and split by sections
      val sections = parseSMTSections(smtModel)
      
      println(s"[Diagnostic] Found ${sections.length} SMT sections")
      sections.foreach { case (name, _) =>
        println(s"  - $name")
      }
      println()
      
      // Add sections incrementally
      for ((sectionName, sectionSMT) <- sections) {
        val sectionStartTime = System.currentTimeMillis()
        println(s"[Diagnostic] Adding section: $sectionName")
        
        session.push(sectionName)
        
        // Ensure .tmp directory exists
        val tmpDir = new File(".tmp")
        if (!tmpDir.exists()) {
          tmpDir.mkdirs()
        }
        
        // Parse this section's SMT and add assertions
        val tempFile = new File(s".tmp/k_section_${sectionName.replaceAll("[^a-zA-Z0-9]", "_")}.smt2")
        val writer = new java.io.PrintWriter(tempFile)
        writer.write(sectionSMT)
        writer.close()
        
        try {
          val sectionAssertions = ctx.parseSMTLIB2File(
            tempFile.getAbsolutePath, Array(), Array(), Array(), Array()
          ).toList.map(_.asInstanceOf[BoolExpr])
          
          sectionAssertions.foreach { expr =>
            session.assert(expr)
          }
          
          val result = session.check()
          val sectionTimeMs = System.currentTimeMillis() - sectionStartTime
          
          result match {
            case Satisfiable(_, _) =>
              println(s"  ✓ SAT (${sectionTimeMs}ms)")
            case Unsatisfiable(core, explanation) =>
              println(s"  ✗ UNSAT - Core: ${core.mkString(", ")}")
            case Unknown(reason, _, timeout) =>
              if (timeout) {
                println(s"  ⏱ TIMEOUT after ${sectionTimeMs}ms!")
              } else {
                println(s"  ? UNKNOWN: $reason")
              }
          }
          
          results += GroupResult(sectionName, result, sectionTimeMs)
          
          // Stop on UNSAT or timeout
          result match {
            case Unsatisfiable(_, _) | Unknown(_, _, true) =>
              return results.toList
            case _ =>
          }
          
        } finally {
          tempFile.delete()
        }
        
        println()
      }
      
    } catch {
      case e: Throwable =>
        println(s"[Diagnostic] ERROR: ${e.getMessage}")
        if (K2Z3.debug) e.printStackTrace()
    }
    
    results.toList
  }
  
  /**
   * Parse SMT model into sections based on comments/headlines
   */
  private def parseSMTSections(smtModel: String): List[(String, String)] = {
    val sections = ListBuffer[(String, String)]()
    val lines = smtModel.split("\n")
    var currentSection = "Preliminaries"
    var currentContent = new StringBuilder()
    
    // Add header (declarations, etc.)
    var inHeader = true
    
    for (line <- lines) {
      // Check for section markers (comments like "; --- Section Name: ---")
      if (line.trim.startsWith(";") && (line.contains("---") || line.contains("==="))) {
        // Save previous section
        if (currentContent.nonEmpty && !inHeader) {
          sections += ((currentSection, currentContent.toString))
        }
        
        // Extract section name
        val sectionName = line
          .replace(";", "")
          .replace("-", "")
          .replace("=", "")
          .trim
          .split(":")
          .headOption
          .getOrElse("Unknown")
          .trim
        
        if (sectionName.nonEmpty && sectionName != "Section Name") {
          currentSection = sectionName
          currentContent = new StringBuilder()
          inHeader = false
        }
      } else {
        currentContent.append(line).append("\n")
      }
    }
    
    // Add last section
    if (currentContent.nonEmpty) {
      sections += ((currentSection, currentContent.toString))
    }
    
    sections.toList
  }
}

