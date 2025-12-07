package k.frontend

object ReservedAnnotations {

  val annotations = Set(
      // Instance control
      AnnotationDecl("noInstances", UnitType),
      AnnotationDecl("instances", IntType),
      
      // Solver configuration
      AnnotationDecl("timeout", IntType),         // Timeout in milliseconds
      AnnotationDecl("bestEffort", UnitType),     // Return partial results on timeout
      AnnotationDecl("incremental", UnitType),    // Enable incremental solving mode
      
      // Function annotations
      AnnotationDecl("opaque", UnitType),         // Mark function as opaque/black-box
      AnnotationDecl("axiom", UnitType),          // Mark constraint as learned axiom
      AnnotationDecl("soft", IntType),            // Soft constraint with weight
      
      // Optimization
      AnnotationDecl("priority", IntType)         // Priority for multi-objective optimization
      )
  
}