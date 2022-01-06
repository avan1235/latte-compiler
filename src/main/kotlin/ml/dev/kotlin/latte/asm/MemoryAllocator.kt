package ml.dev.kotlin.latte.asm

import ml.dev.kotlin.latte.quadruple.*
import ml.dev.kotlin.latte.syntax.BooleanType
import ml.dev.kotlin.latte.syntax.IntType
import ml.dev.kotlin.latte.syntax.StringType
import ml.dev.kotlin.latte.syntax.Type
import ml.dev.kotlin.latte.util.*
import java.util.*

class MemoryAllocator(
  analysis: FlowAnalysis,
) : UndirectedGraph<VirtualReg>() {

  override val nodes: Set<VirtualReg> = analysis.statements.flatMapTo(HashSet()) { it.definedVars() }

  private val connected: DefaultMap<VirtualReg, Set<VirtualReg>> = with(analysis) {
    MutableDefaultMap(withSet<VirtualReg, VirtualReg>()).also { graph ->
      fun Set<VirtualReg>.addEdges(): Unit = forEach { b -> forEach { e -> if (b != e) graph[b] += e } }
      analysis.statements.indices
        .onEach { aliveBefore[it].addEdges() }
        .onEach { aliveAfter[it].addEdges() }
    }
  }

  override fun connected(v: VirtualReg): Set<VirtualReg> = connected[v]

  private var _localsOffset: Int = 0
  val localsOffset: Int get() = _localsOffset
  private val locations = HashMap<String, VarLoc>()
  private val usedArgsLocations = HashSet<Arg>()
  private val argsMovedToReg = HashMap<ArgValue, Reg>()

  private val aliveOnSomeFunctionCall: Set<VirtualReg> = analysis.statements.asSequence().withIndex()
    .filter { it.value is FunCallQ }.map { it.index }
    .flatMapTo(HashSet()) { analysis.aliveOver[it] }

  private val graphColoring =
    GraphColoring(this, ALLOCATED_REGISTERS, this::assignDefault, this::selectToSplit, this::selectColor)

  init {
    graphColoring.coloring.forEach { (virtualReg, varLoc) -> locations[virtualReg.id] = varLoc }
  }

  private val assignedRegisters: HashSet<Reg> =
    HashSet<Reg>().apply { graphColoring.coloring.values.forEach { if (it is Reg) add(it) } }

  val savedByCaller: List<Reg> = CALLEE_SAVED_REGISTERS.intersect(assignedRegisters).toList()

  fun get(value: ValueHolder, strings: Map<String, Label>): VarLoc =
    when (value) {
      is ArgValue -> locations[value.id]
      is LocalValue -> locations[value.id]
      is BooleanConstValue -> Imm(if (value.bool) "1" else "0", BooleanType)
      is IntConstValue -> Imm("${value.int}", IntType)
      is StringConstValue -> Imm(strings[value.str]?.name ?: err("Used not labeled string $this"), StringType)
    } ?: err("Used not defined variable $this")

  fun assureArgLoaded(arg: ArgValue, mov: (Reg, Arg) -> Unit): VarLoc? {
    val loc = locations[arg.id]
    val reg = argsMovedToReg[arg]
    if (reg == loc) reg?.let { mov(reg, Arg(arg.offset, arg.type)) }.then { argsMovedToReg -= arg }
    return loc
  }

  private fun selectToSplit(withEdges: TreeMap<Int, HashSet<VirtualReg>>): VirtualReg {
    val descendingCounts = withEdges.descendingKeySet()
    return descendingCounts.firstNotNullOfOrNull { withEdges[it]?.intersect(aliveOnSomeFunctionCall)?.firstOrNull() }
      ?: descendingCounts.firstNotNullOfOrNull { withEdges[it]?.firstOrNull() }
      ?: withEdges.values.last().first()
  }

  private fun selectColor(forReg: VirtualReg, available: Set<Reg>, coloring: Map<VirtualReg, VarLoc>): Reg {
    val regCounts = coloring.values.filterIsInstance<Reg>().groupingBy { it }.eachCountTo(EnumMap(Reg::class.java))
    val loc = available.minByOrNull { regCounts[it] ?: 0 } ?: available.first()
    if (forReg is ArgValue) argsMovedToReg[forReg] = loc
    return loc
  }

  private fun assignDefault(register: VirtualReg): Mem = when (register) {
    is LocalValue -> reserveLocal(register.type)
    is ArgValue -> reserveArg(register)
  }

  private fun reserveLocal(type: Type): Loc = Loc(_localsOffset.also { _localsOffset += type.size }, type)

  private fun reserveArg(register: ArgValue): Mem =
    when (val arg = Arg(register.offset, register.type)) {
      in usedArgsLocations -> reserveLocal(register.type)
      else -> arg
    }
}

private val ALLOCATED_REGISTERS = Reg.values().toSet() - RESERVED_REGISTERS

private fun err(message: String): Nothing = throw MemoryAllocationException(message.msg)
