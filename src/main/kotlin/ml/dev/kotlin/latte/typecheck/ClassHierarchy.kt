package ml.dev.kotlin.latte.typecheck

import ml.dev.kotlin.latte.asm.CLASS_FIELDS_OFFSET
import ml.dev.kotlin.latte.syntax.*
import ml.dev.kotlin.latte.util.*

class ClassHierarchy {
  private val classes: HashMap<String, ClassDefNode> = HashMap()
  private val classParents: MutableDefaultMap<String, LinkedHashSet<String>> = MutableDefaultMap({ LinkedHashSet() })
  private val classChildren: MutableDefaultMap<String, HashSet<String>> = MutableDefaultMap({ HashSet() })

  private val _classFields: MutableDefaultMap<String, LinkedHashMap<String, Type>> =
    MutableDefaultMap({ LinkedHashMap() })

  private val argsCombinationsCache: DefaultMap<List<Type>, Set<List<Type>>> = MutableDefaultMap({ args ->
    args.map {
      if (it is RefType) orderedClassParents(it.typeName).mapTo(ArrayList(), ::RefType)
      else arrayListOf(it)
    }.combinations()
  })
  private val _classMethods: MutableDefaultMap<String, FunEnv> = MutableDefaultMap({ FunEnv(argsCombinationsCache) })

  val functions: FunEnv = FunEnv(argsCombinationsCache, createStdLibFunEnv())
  val classMethods: DefaultMap<String, FunEnv> = _classMethods
  val classFields: DefaultMap<String, Map<String, ClassField>> = MutableDefaultMap({ orderedClassFields(it) })
  val classSizeBytes: DefaultMap<String, Bytes> = MutableDefaultMap({ className ->
    _classFields[className].values.sumOf { it.size }
  })

  fun addClass(classNode: ClassDefNode): Unit = with(classNode) {
    if (ident in RESERVED_IDENTIFIERS) err("Cannot define class with name $ident")
    if (ident in classes.keys) err("Redefined class with name $ident")
    classes[ident] = this
    classParents[ident] = parentClass?.let { linkedSetOf(it) } ?: linkedSetOf()
    parentClass?.let { classChildren[it] += ident }
  }

  operator fun plusAssign(funDef: FunDefNode): Unit = functions.plusAssign(funDef)

  fun buildClassStructure(): Unit = when (val nodes = ClassHierarchyGraph().topologicalSort()) {
    is WithCycle -> {
      val problem = nodes.cycle.map { classes[it]!! }
      val cycleNames = problem.joinToString { it.ident }
      problem.first().err("Detected cycle in class hierarchy node $cycleNames so class structure cannot be built")
    }
    is Sorted -> nodes.sorted.forEach { classes[it]!!.buildStructure() }
  }

  fun functionsTypesByMangledName(): Map<String, Type> = buildMap {
    val addAllFunctionsByName = { env: FunEnv -> env.ordered().forEach { put(it.name, it.ret) } }
    addAllFunctionsByName(functions)
    _classMethods.values.forEach(addAllFunctionsByName)
  }

  infix fun Type.isSubTypeOf(of: Type?): Boolean = when {
    this == PrimitiveType.VoidRefType && of is RefType -> true
    this is PrimitiveType && of is PrimitiveType -> this == of
    this is RefType && of is RefType -> typeName == of.typeName || of.typeName in classParents[typeName]
    else -> false
  }

  fun isTypeDefined(type: Type): Boolean = when (type) {
    is PrimitiveType -> true
    is RefType -> type.typeName in classes
  }

  private fun orderedClassFields(className: String): Map<String, ClassField> {
    var offset = CLASS_FIELDS_OFFSET
    return _classFields[className].entries
      .associate { it.key to ClassField(it.key, it.value, offset.apply { offset += it.value.size }) }
  }

  private fun ClassDefNode.buildStructure() {
    if (parentClass != null) classParents[ident] += classParents[parentClass]
    addFields()
    addMethods()
  }

  private fun ClassDefNode.addFields() {
    val thisClassFields = _classFields[ident]
    parentClass?.let { thisClassFields += _classFields[it] }
    for (field in fields) {
      val fieldName = field.ident
      if (fieldName in thisClassFields) err("Redefined field $fieldName in class or its parent class")
      thisClassFields[fieldName] = field.type
    }
  }

  private fun ClassDefNode.addMethods() {
    val parentClassMethods = parentClass?.let { _classMethods[it] } ?: FunEnv(argsCombinationsCache)
    val thisClassMethods = _classMethods[ident].also { it += parentClassMethods }
    for (method in methods) thisClassMethods[ident] = method
  }

  private fun orderedClassParents(className: String): Sequence<String> = sequence {
    yield(className)
    yieldAll(classParents[className])
  }

  private inner class ClassHierarchyGraph : DirectedGraph<String> {
    override val nodes: Set<String> get() = classes.keys.toHashSet()
    override fun successors(v: String): Set<String> = classChildren[v]
    override fun predecessors(v: String): Set<String> = setOfNotNull(classParents[v].firstOrNull())
  }
}

private val RESERVED_IDENTIFIERS: Set<String> = PrimitiveType.values().mapTo(HashSet()) { it.typeName }

data class ClassField(val name: String, val type: Type, val offset: Bytes)

private fun AstNode.err(message: String): Nothing = throw ClassHierarchyException(LocalizedMessage(message, span?.from))
