package k.frontend

import java.math.{BigDecimal => BD, BigInteger}
import java.nio.file.{Files, Path, Paths}
import java.time.Instant
import scala.collection.concurrent.TrieMap
import scala.jdk.CollectionConverters._

/**
 * Piecewise-linear timelines and event times loaded from CSV when the model is processed.
 *
 * K surface (intrinsics, no new grammar):
 *   timelineValue("file.csv", t, "ms")  : Duration   -- file value column is seconds; t is a Time
 *   eventTime("file.csv", "eventName")  : Time       -- file columns: event,time
 *   constDuration("file.csv", "NAME")   : Duration   -- file columns: name,seconds
 *
 * timelineValue(t) lowers to a balanced if-then-else tree over the segments, with constant
 * coefficients only, so it is linear arithmetic in microseconds (t is the only variable):
 *   v = V_k + (V_k+1 - V_k)/(T_k+1 - T_k) * (t - T_k)    for T_k <= t < T_k+1
 * Rounding ("ms" or "us") reproduces Math.round(seconds*1000) style conversions: floor(x + 0.5).
 * An optional third CSV column names a segment (e.g. a flyby); between segments the value is held constant.
 * Outside the sampled range the first/last segment is extrapolated; models must bound t.
 */
object TimelineSupport {
  case class Timeline(times: Array[Long], valuesUs: Array[BD], segments: Array[String])

  private val timelineCache = TrieMap[String, Timeline]()
  private val eventCache = TrieMap[String, Map[String, Long]]()
  private var counter = 0
  @volatile var used = false

  private def resolve(file: String): Path = {
    val p = Paths.get(file)
    if (p.isAbsolute || Files.exists(p)) p
    else Option(System.getenv("KDATA_DIR")).map(d => Paths.get(d).resolve(file)).filter(Files.exists(_)).getOrElse(p)
  }

  private def parseInstantUs(s: String): Long = {
    val i = Instant.parse(s.trim)
    Math.addExact(Math.multiplyExact(i.getEpochSecond, 1000000L), (i.getNano / 1000).toLong)
  }

  def loadTimeline(file: String): Timeline = timelineCache.getOrElseUpdate(file, {
    val rows = Files.readAllLines(resolve(file)).asScala.drop(1).filter(_.trim.nonEmpty).map(_.split(",").map(_.trim))
    if (rows.size < 2) UtilSMT.error(s"Timeline $file needs at least two points")
    val ts = rows.map(r => parseInstantUs(r(0))).toArray
    val vs = rows.map(r => new BD(r(1)).movePointRight(6)).toArray
    for (i <- 1 until ts.length) if (ts(i) <= ts(i - 1)) UtilSMT.error(s"Timeline $file: times must increase (row ${i + 1})")
    // optional third column: segment id; the value is held (constant) between the last point of one segment and the first of the next
    val sg = rows.map(r => if (r.length > 2) r(2) else "").toArray
    Timeline(ts, vs, sg)
  })

  def loadEvents(file: String): Map[String, Long] = eventCache.getOrElseUpdate(file,
    Files.readAllLines(resolve(file)).asScala.drop(1).filter(_.trim.nonEmpty).map { l =>
      val c = l.split(",").map(_.trim); c(0) -> parseInstantUs(c(1))
    }.toMap)

  /** Exact rational num/den as an SMT Real term. */
  private def ratSMT(num: BigInteger, den: BigInteger): String = {
    val g = num.gcd(den)
    val (n, d) = if (g.signum == 0) (num, den) else (num.divide(g), den.divide(g))
    val ns = if (n.signum < 0) s"(- ${n.negate})" else n.toString
    s"(/ $ns $d)"
  }

  /** Exact SMT Real term for x / divisor. */
  private def decimalOver(x: BD, divisor: Long): String = {
    val (unscaled, scale) = if (x.scale < 0) (x.toBigInteger, 0) else (x.unscaledValue, x.scale)
    ratSMT(unscaled, BigInteger.TEN.pow(scale).multiply(BigInteger.valueOf(divisor)))
  }

  private def strArg(a: Argument, what: String): String = a match {
    case PositionalArgument(StringLiteral(s)) => s.stripPrefix("\"").stripSuffix("\"")
    case _ => UtilSMT.error(s"$what must be a string literal")
  }

  def eventTimeSMT(args: List[Argument]): String = {
    if (args.length != 2) UtilSMT.error("eventTime(file, name) takes 2 arguments")
    val f = strArg(args(0), "eventTime file"); val n = strArg(args(1), "eventTime name")
    loadEvents(f).getOrElse(n, UtilSMT.error(s"eventTime: no event '$n' in $f")).toString
  }

  def constDurationSMT(args: List[Argument]): String = {
    if (args.length != 2) UtilSMT.error("constDuration(file, name) takes 2 arguments")
    val f = strArg(args(0), "constDuration file"); val n = strArg(args(1), "constDuration name")
    val row = Files.readAllLines(resolve(f)).asScala.drop(1).map(_.split(",").map(_.trim)).find(_(0) == n)
      .getOrElse(UtilSMT.error(s"constDuration: no constant '$n' in $f"))
    new BD(row(1)).movePointRight(6).setScale(0, java.math.RoundingMode.HALF_UP).toBigInteger.toString
  }

  def valueAtSMT(args: List[Argument], className: String, subTyping: Boolean): String = {
    if (args.length != 3) UtilSMT.error("timelineValue(file, t, rounding) takes 3 arguments")
    val tl = loadTimeline(strArg(args(0), "timelineValue file"))
    val unitUs = strArg(args(2), "timelineValue rounding") match {
      case "ms" => 1000
      case "us" => 1
      case o    => UtilSMT.error(s"timelineValue rounding must be \"ms\" or \"us\", got $o")
    }
    used = true
    counter += 1
    val tv = s"tl_t$counter"
    val tSMT = args(1).toSMT(className, subTyping)
    def seg(k: Int): String = {
      if (tl.segments(k) != tl.segments(k + 1)) return decimalOver(tl.valuesUs(k), 1)
      val dt = tl.times(k + 1) - tl.times(k)
      val dv = tl.valuesUs(k + 1).subtract(tl.valuesUs(k))
      s"(+ ${decimalOver(tl.valuesUs(k), 1)} (* ${decimalOver(dv, dt)} (to_real (- $tv ${tl.times(k)}))))"
    }
    def tree(lo: Int, hi: Int): String =
      if (lo == hi) seg(lo)
      else { val mid = (lo + hi + 1) / 2; s"(ite (< $tv ${tl.times(mid)}) ${tree(lo, mid - 1)} ${tree(mid, hi)})" }
    s"(let (($tv $tSMT)) (* $unitUs (to_int (+ (/ ${tree(0, tl.times.length - 2)} $unitUs) (/ 1 2)))))"
  }
}
