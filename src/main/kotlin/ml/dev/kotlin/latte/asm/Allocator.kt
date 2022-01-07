package ml.dev.kotlin.latte.asm

import ml.dev.kotlin.latte.quadruple.*
import ml.dev.kotlin.latte.syntax.BooleanType
import ml.dev.kotlin.latte.syntax.IntType
import ml.dev.kotlin.latte.syntax.StringType
import ml.dev.kotlin.latte.syntax.Type
import ml.dev.kotlin.latte.util.GraphColoring
import ml.dev.kotlin.latte.util.MemoryAllocationException
import ml.dev.kotlin.latte.util.msg
import ml.dev.kotlin.latte.util.then
import kotlin.collections.set

class Allocator(
  analysis: FlowAnalysis,
  strategy: AllocatorStrategyProducer,
) {
  private var _localsOffset: Int = 0
  val localsOffset: Int get() = _localsOffset
  private val locations = HashMap<String, VarLoc>()
  private val argsMovedToReg = HashMap<ArgValue, Reg>()

  private val memoryManager = object : MemoryManager {
    override fun reserveLocal(type: Type): Loc = Loc(_localsOffset.also { _localsOffset += type.size }, type)
    override fun reserveArg(register: ArgValue): Arg = Arg(register.offset, register.type)
    override fun moveArgToReg(arg: ArgValue, reg: Reg) {
      argsMovedToReg[arg] = reg
    }
  }

  private val graphColoring =
    GraphColoring(RegisterInferenceGraph(analysis), ALLOCATED_REGISTERS, strategy(analysis, memoryManager))

  private val assignedRegisters: HashSet<Reg> =
    HashSet<Reg>().apply { graphColoring.coloring.values.forEach { if (it is Reg) add(it) } }

  val savedByCallee: List<Reg> = CALLEE_SAVED_REGISTERS.intersect(assignedRegisters).toList()

  init {
    graphColoring.coloring.forEach { (virtualReg, varLoc) -> locations[virtualReg.id] = varLoc }
  }

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
}

typealias AllocatorStrategyProducer = (FlowAnalysis, MemoryManager) -> AllocatorStrategy

private val ALLOCATED_REGISTERS = Reg.values().toSet() - RESERVED_REGISTERS

private fun err(message: String): Nothing = throw MemoryAllocationException(message.msg)
