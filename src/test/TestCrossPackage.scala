package test

import k.frontend._
import java.io.File

object TestCrossPackage {
  def main(args: Array[String]): Unit = {
    println("=== Testing cross-package member type reference ===\n")
    
    // Test pkg2.k which imports pkg1 and uses Helper as a member type
    val file = new File("src/examples/pkg2.k")
    println(s"Testing file: ${file.getAbsolutePath}")
    
    // Set the classpath so imports can be found
    Frontend.classpath = Set("src/examples")
    println(s"Classpath set to: ${Frontend.classpath}")
    
    try {
      TypeChecker.reset
      val parsedModel = Frontend.getModelFromFile(file.toString)
      
      // Combine the model with its imports (this flattens declarations)
      println(s"\n--- Before combining: ---")
      println(s"Parsed model package: ${parsedModel.packageName}")
      println(s"Parsed model decls: ${parsedModel.decls.length}")
      println(s"Parsed model imports: ${parsedModel.imports.length}")
      println(s"Parsed model packages: ${parsedModel.packages.length}")
      
      val model = Frontend.combineModel(parsedModel, file.toString)
      
      if (model != null) {
        println(s"\n✅ Model loaded successfully (after combining)")
        println(s"Package name: ${model.packageName.getOrElse("(none)")}")
        println(s"Number of declarations: ${model.decls.length}")
        println(s"Number of imports: ${model.imports.length}")
        println(s"Number of packages: ${model.packages.length}")
        
        println("\n--- Imports: ---")
        model.imports.foreach { imp =>
          println(s"  import ${imp.name}")
        }
        
        println("\n--- Top-level declarations in combined model: ---")
        model.decls.foreach { decl =>
          decl match {
            case cd: EntityDecl =>
              println(s"  Class: ${cd.ident}")
              cd.members.foreach {
                case pd: PropertyDecl =>
                  println(s"    Property: ${pd.name} : ${pd.ty}")
                case md: MemberDecl =>
                  println(s"    Member: ${md.getClass.getSimpleName}")
              }
            case _ =>
              println(s"  Other: ${decl.getClass.getSimpleName}")
          }
        }
        
        println("\n--- Packages in combined model: ---")
        model.packages.foreach { pkg =>
          println(s"Package: ${pkg.name}")
          println(s"  Model imports: ${pkg.model.imports.length}")
          println(s"  Model decls: ${pkg.model.decls.length}")
          println(s"  Model packages: ${pkg.model.packages.length}")
          pkg.model.decls.foreach {
            case cd: EntityDecl =>
              println(s"    Class: ${cd.ident}")
            case _ =>
          }
          pkg.model.packages.foreach { subpkg =>
            println(s"  Sub-package: ${subpkg.name}")
            println(s"    Sub-model decls: ${subpkg.model.decls.length}")
            subpkg.model.decls.foreach {
              case cd: EntityDecl =>
                println(s"      Class: ${cd.ident}")
              case _ =>
            }
          }
        }
        
        // Type check
        println("\n--- Running type checker ---")
        val tc = new TypeChecker(model)
        tc.smtCheck
        println("✅ Type checking passed")
        
        // Check what entities are in the final model
        println("\n--- Entity declarations after import: ---")
        val entityDecls = model.allEntityDecls(model)
        entityDecls.foreach { ed =>
          println(s"  Entity: ${ed.ident} (from ${ed.fqName})")
        }
        
        // Now generate SMT to see what instances are created
        println("\n--- Generating SMT (creates heap layout) ---")
        val smt = model.toSMT
        println("\nSMT output (first 2000 chars):")
        println(smt.take(2000))
        
        // Check the object graph
        println("\n--- Checking object graph (heap layout) ---")
        if (UtilSMT.objectGraph != null) {
          println(UtilSMT.objectGraph.toString)
        } else {
          println("  No object graph created")
        }
        
      } else {
        println("❌ Failed to load model")
      }
    } catch {
      case e: Exception =>
        println(s"❌ Error: ${e.getMessage}")
        e.printStackTrace()
    }
  }
}
