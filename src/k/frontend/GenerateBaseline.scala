package k.frontend

import java.io._
import java.nio.file.Paths
import org.json._

object GenerateBaseline {
  def main(args: Array[String]): Unit = {
    if (args.length < 2) {
      System.err.println("Usage: GenerateBaseline <k-file> <output-json>")
      System.exit(1)
    }
    
    val kFile = new File(args(0))
    val kFilePath = args(0)
    
    // Set up classpath like Frontend.main does
    val modelFileDirectory = 
      if (Paths.get(kFilePath).getParent == null) new java.io.File(".").getCanonicalPath
      else Paths.get(kFilePath).getParent.toString
    Frontend.classpath = Frontend.classpath + modelFileDirectory
    
    // Reset state
    TypeChecker.reset
    UtilSMT.reset
    K2Z3.debug = false
    K2Z3.silent = true
    ASTOptions.debug = false
    ASTOptions.silent = true
    TypeChecker.silent = true
    TypeChecker.debug = false
    
    val baseline = new JSONObject()
    baseline.put("name", kFile.getName)
    
    try {
      // Get model with imports processed (like main does)
      val rawModel = Frontend.getModelFromFile(kFilePath)
      
      // Process imports at the top level (for top-level imports)
      var s: Set[String] = Set(new File(kFilePath).getAbsolutePath)
      val topImportModels = Frontend.processImports(rawModel, s)._1
      
      // Process imports inside packages (for package-level imports like "package a import d")
      val processedPackages = Frontend.combinePackages(rawModel.packages)
      
      // Combine everything
      val combinedDecls = rawModel.decls ++ topImportModels.flatMap(_.decls)
      val model = Model(rawModel.packageName, processedPackages, rawModel.imports, 
                        rawModel.annotations, combinedDecls)
      
      // Type check
      new TypeChecker(model).smtCheck
      
      // Generate outputs
      ASTOptions.useJson1 = true
      val json1 = model.toJson
      ASTOptions.useJson1 = false
      val json2 = model.toJson
      val smt = model.toSMT
      
      val smtModel = if (smt != null) {
        val res = Frontend.runWithTimeout(Frontend.timeoutValue) {
          K2Z3.solveSMT(model, smt, false)
        }
        if (res.isEmpty) null
        else if (K2Z3.z3Model != null) K2Z3.z3Model.toString
        else null
      } else null
      
      baseline.put("model", model.toString)
      baseline.put("json1", json1)
      baseline.put("json2", json2)
      baseline.put("smt", smt)
      baseline.put("smtModel", smtModel)
      baseline.put("typeChecks", true)
      
    } catch {
      case TypeCheckException =>
        baseline.put("model", "")
        baseline.put("json1", "")
        baseline.put("json2", "")
        baseline.put("smt", "")
        baseline.put("smtModel", "")
        baseline.put("typeChecks", false)
      case e: Throwable =>
        System.err.println(s"Exception: ${e.getClass.getName}: ${e.getMessage}")
        baseline.put("model", "")
        baseline.put("json1", "")
        baseline.put("json2", "")
        baseline.put("smt", "")
        baseline.put("smtModel", "")
        baseline.put("typeChecks", false)
    }
    
    val fw = new FileWriter(args(1))
    fw.write(baseline.toString(2))
    fw.close()
    
    println("✓ Baseline saved to " + args(1))
  }
}
