# Heap & CEGAR Implementation Notes (K / Klang)

This file collects implementation instructions and code snippets for
heap strategies and CEGAR in the unified solver.

---

## 1. Heap Strategy Abstraction

Add a heap strategy enum and bounds helper in `UnifiedSolver.scala`:

```scala
sealed trait HeapStrategy
object HeapStrategy {
  /** Do not bound heap at all (effectively what we have today). */
  case object Unbounded extends HeapStrategy

  /** Fixed bounds via objectBounds; treat them as hard constraints. */
  case object Bounded extends HeapStrategy

  /** Fixed bounds, but refined in a CEGAR loop. */
  case object CegarBounded extends HeapStrategy
}

/** Heap bounds per class (or global). */
final case class HeapBounds(
  defaultBound: Int,
  perClassBounds: Map[String, Int] = Map.empty,
  maxBound: Int = 1024
) {
  def boundForClass(cls: String): Int =
    perClassBounds.getOrElse(cls, defaultBound)

  def withIncreasedBound(cls: String, factor: Int = 2): HeapBounds = {
    val old  = boundForClass(cls)
    val next = math.min(old * factor, maxBound)
    copy(perClassBounds = perClassBounds + (cls -> next))
  }
}
```

Inside `object UnifiedSolver`:

```scala
object UnifiedSolver {
  import HeapStrategy._

  // Global default; can be overridden per model via annotations or CLI.
  var heapStrategy: HeapStrategy = HeapStrategy.CegarBounded

  // Global default bounds.
  var heapBounds: HeapBounds = HeapBounds(defaultBound = 4, maxBound = 256)

  // ...
}
```

Extend `SolveConfig` and `extractConfig`:

```scala
case class SolveConfig(
  timeout:    Option[Long]   = None,
  bestEffort: Boolean        = false,
  maxObjects: Option[Int]    = None,
  heapStrategy: HeapStrategy = heapStrategy  // default from global
)

private def extractConfig(model: KModel): SolveConfig = {
  var timeout:    Option[Long] = None
  var bestEffort = false
  var maxObjects: Option[Int]  = None
  var strategy:   HeapStrategy = heapStrategy

  if (model != null) {
    for (decl <- model.decls) decl match {
      case ed: EntityDecl =>
        for (ann <- ed.annotations) ann match {
          case Annotation("timeout", IntegerLiteral(ms)) =>
            timeout = Some(ms.toLong)
          case Annotation("bestEffort", _) =>
            bestEffort = true
          case Annotation("maxObjects", IntegerLiteral(n)) =>
            maxObjects = Some(n.toInt)
          case Annotation("heapStrategy", StringLiteral(s)) =>
            strategy = s.toLowerCase match {
              case "unbounded"    => HeapStrategy.Unbounded
              case "bounded"      => HeapStrategy.Bounded
              case "cegarbounded" => HeapStrategy.CegarBounded
              case _              => heapStrategy
            }
          case _ =>
        }
      case _ =>
    }
  }

  SolveConfig(timeout, bestEffort, maxObjects, strategy)
}
```

---

## 2. Heap Encoding Strategies (Conceptual)

### 2.1 Infinite / Uninterpreted Heap (Unbounded)

Objects as an uninterpreted sort, no finite bound:

```smt2
(declare-sort Obj_C 0)

(declare-fun f_C (Obj_C) Int)   ; example field

(declare-const null_C Obj_C)
(declare-const o1 Obj_C)
(assert (distinct o1 null_C))
; ...
```

No heap-size guessing; no cardinality reasoning.

### 2.2 Bounded Pool With Soft Constraints

Pool of `N_C` candidate objects, plus `Alive_C` predicate:

```smt2
(declare-sort Obj_C 0)
(declare-const c1 Obj_C)
(declare-const c2 Obj_C)
; ...

(declare-fun Alive_C (Obj_C) Bool)

(assert (distinct c1 c2 ...))

(assert-soft (not (Alive_C c1)) :weight 1)
(assert-soft (not (Alive_C c2)) :weight 1)
; ...
```

Use `Optimize` to minimize live objects subject to hard constraints.

### 2.3 Bounded Pool + CEGAR Over Bounds

Same as 2.2, but bounds `N_C` are refined in a CEGAR loop:

- Start small.
- Solve.
- If model/unsat suggests heap too small, increase `N_C` and re-solve.

---

## 3. Integration into Unified Loop

Define a wrapper result type:

```scala
sealed trait HeapSolveResult
object HeapSolveResult {
  case class Sat(model: Z3Model, bounds: HeapBounds) extends HeapSolveResult
  case class Unsat(bounds: HeapBounds)               extends HeapSolveResult
  case class Unknown(reason: String, bounds: HeapBounds) extends HeapSolveResult
}

final case class HeapEncodingConfig(
  strategy: HeapStrategy,
  bounds:   HeapBounds
)
```

Add a `solveModelWithHeap` entrypoint that dispatches on strategy:

```scala
def solveModelWithHeap(
  model: KModel,
  smtModel: String,   // base SMT (without heap instrumentation)
  config: SolveConfig,
  printModel: Boolean
): HeapSolveResult = {

  val cfg = HeapEncodingConfig(config.heapStrategy, UnifiedSolver.heapBounds)

  config.heapStrategy match {
    case HeapStrategy.Unbounded =>
      val currentSMT = generateSMT(model, smtModel)
      val status     = solveWithTimeout(model, currentSMT, config)
      status match {
        case SolveResult.Sat(m)   => HeapSolveResult.Sat(m, cfg.bounds)
        case SolveResult.Unsat    => HeapSolveResult.Unsat(cfg.bounds)
        case SolveResult.Timeout  =>
          HeapSolveResult.Unknown("timeout", cfg.bounds)
      }

    case HeapStrategy.Bounded =>
      val currentSMT = generateSMT(model, smtModel)
      val status     = solveWithTimeout(model, currentSMT, config)
      status match {
        case SolveResult.Sat(m)   => HeapSolveResult.Sat(m, cfg.bounds)
        case SolveResult.Unsat    => HeapSolveResult.Unsat(cfg.bounds)
        case SolveResult.Timeout  =>
          HeapSolveResult.Unknown("timeout", cfg.bounds)
      }

    case HeapStrategy.CegarBounded =>
      solveModelWithHeapCegar(model, smtModel, cfg, config, printModel)
  }
}
```

CEGAR loop skeleton:

```scala
def solveModelWithHeapCegar(
  model: KModel,
  smtModel: String,
  initialCfg: HeapEncodingConfig,
  config: SolveConfig,
  printModel: Boolean
): HeapSolveResult = {

  var cfg        = initialCfg
  var attempt    = 0
  var lastResult = Option.empty[HeapSolveResult]

  clearInterrupt()

  while (!interrupted && attempt < 20) {
    attempt += 1
    log(s"[Heap CEGAR] Attempt $attempt with bounds: ${cfg.bounds}")

    val currentSMT = generateSMT(model, smtModel)
    val status     = solveWithTimeout(model, currentSMT, config)

    status match {
      case SolveResult.Sat(z3Model) =>
        bestSoFar = Some(z3Model)
        if (printModel) PrintModel(model)

        if (heapBoundClearlyInsufficient(model, z3Model, cfg.bounds)) {
          log("[Heap CEGAR] Model suggests heap too small; refining bounds")
          cfg = cfg.copy(bounds = refineHeapBoundsFromModel(model, z3Model, cfg.bounds))
          lastResult = Some(HeapSolveResult.Sat(z3Model, cfg.bounds))
        } else {
          return HeapSolveResult.Sat(z3Model, cfg.bounds)
        }

      case SolveResult.Unsat =>
        log("[Heap CEGAR] UNSAT under current bounds")
        if (heapBoundsCouldCauseUnsat(model, cfg.bounds)) {
          cfg = cfg.copy(bounds = refineHeapBoundsFromUnsat(model, cfg.bounds))
          lastResult = Some(HeapSolveResult.Unsat(cfg.bounds))
        } else {
          return HeapSolveResult.Unsat(cfg.bounds)
        }

      case SolveResult.Timeout =>
        log("[Heap CEGAR] TIMEOUT")
        return HeapSolveResult.Unknown("timeout", cfg.bounds)
    }
  }

  lastResult.getOrElse {
    bestSoFar.map(m => HeapSolveResult.Sat(m, cfg.bounds))
      .getOrElse(HeapSolveResult.Unknown("max-iterations", cfg.bounds))
  }
}
```

Hook helpers:

```scala
def heapBoundClearlyInsufficient(
  model: KModel,
  z3Model: Z3Model,
  bounds: HeapBounds
): Boolean = {
  // For now, just use checkNeedMoreObjects
  checkNeedMoreObjects(z3Model)
}

def heapBoundsCouldCauseUnsat(
  model: KModel,
  bounds: HeapBounds
): Boolean = {
  // Simple heuristic: assume UNSAT might be due to heap bounds
  true
}

def refineHeapBoundsFromModel(
  model: KModel,
  z3Model: Z3Model,
  bounds: HeapBounds
): HeapBounds = {
  bounds.copy(defaultBound = math.min(bounds.defaultBound * 2, bounds.maxBound))
}

def refineHeapBoundsFromUnsat(
  model: KModel,
  bounds: HeapBounds
): HeapBounds = {
  bounds.copy(defaultBound = math.min(bounds.defaultBound * 2, bounds.maxBound))
}
```

---

## 4. SMT Instrumentation: `k!heap_full!ClassName` Flags

Extend `generateSMT(model, smtModel)` to declare per-class flags:

```scala
private def generateSMT(model: KModel, baseSMT: String): String = {
  val sb = new StringBuilder(baseSMT)

  // 1. CEGAR refinements (external functions)
  if (refinements.nonEmpty) {
    sb.append("\n; CEGAR Refinements\n")
    refinements.foreach(r => sb.append(r).append('\n'))
  }

  // 2. Heap bound instrumentation
  if (dynamicClasses.nonEmpty) {
    sb.append("\n; Heap bound instrumentation\n")
    for (className <- dynamicClasses) {
      val bound = objectBounds.getOrElse(className, 0)
      sb.append(s"(declare-const k!heap_full!$className Bool)\n")
      sb.append(s"; TODO: relate k!heap_full!$className to heap usage for $className (bound = $bound)\n")
    }
  }

  sb.toString
}
```

Later, tie these flags to the actual heap encoding, e.g.:

```smt2
; (assert (= k!heap_full!MyClass (= num_MyClass N_MyClass)))
```

---

## 5. `checkNeedMoreObjects` and Refinement Hooks

Implement `checkNeedMoreObjects` to read the flags from a model:

```scala
private def checkNeedMoreObjects(z3Model: Z3Model): Boolean = {
  if (dynamicClasses.isEmpty) return false

  var needsMore = false

  for (className <- dynamicClasses if !needsMore) {
    val flagName = s"k!heap_full!$className"
    try {
      val flagExpr = K2Z3.ctx.mkBoolConst(flagName)
      val interp   = z3Model.getConstInterp(flagExpr)

      if (interp != null && interp.isInstanceOf[BoolExpr]) {
        val be = interp.asInstanceOf[BoolExpr]
        if (be.isTrue) {
          log(s"[Heap] Bound appears tight for class $className")
          needsMore = true
        }
      }
    } catch {
      case _: Throwable =>
        // Missing flag or type mismatch: ignore and be conservative
    }
  }

  needsMore
}
```

The refinement helpers (already given above) use this to decide when to increase bounds.

---

## 6. Optional: Optimize + Soft Constraints for Heap Minimization

In the bounded-pool encoding, for each candidate object `c_i` of class `C` introduce `Alive_C(c_i)` and soft constraints:

```smt2
(declare-sort Obj_C 0)
(declare-const c1 Obj_C)
(declare-const c2 Obj_C)
; ...

(declare-fun Alive_C (Obj_C) Bool)

(assert-soft (not (Alive_C c1)) :weight 1)
(assert-soft (not (Alive_C c2)) :weight 1)
; ...
```

On the Scala side (e.g. `K2Z3`):

```scala
hasSoftConstraints = true
useOptimize        = true

val smt = generateSMT(model, smtModel)  // includes assert-soft for Alive_C
solveSMTWithOptimize(model, smt, printModel = true)
```

`solveSMTWithOptimize` should:

- parse SMT-LIB2,
- add assertions and soft assertions to an `Optimize` instance,
- call `opt.Check()` and extract a model that satisfies hard constraints and minimizes violated soft constraints (i.e., minimizes number of live objects).

You can combine this with the CEGAR loop by running Optimize inside each iteration and still using `k!heap_full!ClassName` flags to determine when to bump bounds.

---
