package ml.dev.kotlin.latte.asm

import ml.dev.kotlin.latte.quadruple.*
import ml.dev.kotlin.latte.syntax.Type
import ml.dev.kotlin.latte.util.GraphColoring
import ml.dev.kotlin.latte.util.MemoryAllocationException
import ml.dev.kotlin.latte.util.msg
import kotlin.collections.set

class Allocator(
  analysis: FlowAnalysis,
  strategy: AllocatorStrategyProducer,
) {
  private var _localsOffset: Int = 0
  val localsOffset: Int get() = _localsOffset
  private val locations = HashMap<String, VarLoc>()

  private val memoryManager = object : MemoryManager {
    override fun reserveLocal(id: String, type: Type): Loc =
      Loc(_localsOffset.also { _localsOffset += type.size }, type).also { locations[id] = it }

    override fun reserveArg(register: ArgValue): Arg =
      Arg(register.offset, register.type).also { locations[register.id] = it }
  }

  private val graphColoring =
    GraphColoring(RegisterInferenceGraph(analysis), ALLOCATED_REGISTERS, strategy(analysis, memoryManager))

  private val assignedRegisters: HashSet<Reg> =
    HashSet<Reg>().apply { graphColoring.coloring.values.forEach { if (it is Reg) add(it) } }

  private val definedArgs = analysis.definedAt[0].filterIsInstance<ArgValue>()

  val savedByCallee: List<Reg> = CALLEE_SAVED_REGISTERS.intersect(assignedRegisters).toList()

  init {
    graphColoring.coloring.forEach { (localValue, varLoc) -> locations[localValue.id] = varLoc }
    definedArgs.forEach { memoryManager.reserveArg(it) }
  }

  fun get(value: ValueHolder, strings: Map<String, Label>): VarLoc =
    when (value) {
      is ArgValue -> locations[value.id]
      is LocalValue -> locations[value.id]
      is BooleanConstValue -> Imm(if (value.bool) "1" else "0", value.type)
      is IntConstValue -> Imm("${value.int}", value.type)
      is StringConstValue -> Imm(strings[value.str]?.name ?: err("Used not labeled string $this"), value.type)
      is LabelConstValue -> Imm(value.label.name, value.type)
      is NullConstValue -> Imm("0", value.type)
    } ?: err("Used not defined variable $value")
}

interface MemoryManager {
  fun reserveLocal(id: String, type: Type): Loc
  fun reserveArg(register: ArgValue): Arg
}

typealias AllocatorStrategyProducer = (FlowAnalysis, MemoryManager) -> AllocatorStrategy

private val ALLOCATED_REGISTERS = Reg.values().toSet() - RESERVED_REGISTERS

private fun err(message: String): Nothing = throw MemoryAllocationException(message.msg)
