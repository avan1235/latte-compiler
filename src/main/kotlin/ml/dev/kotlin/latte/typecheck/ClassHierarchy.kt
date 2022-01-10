package ml.dev.kotlin.latte.typecheck

import ml.dev.kotlin.latte.syntax.*
import ml.dev.kotlin.latte.util.*

data class ClassHierarchy(
  private val classes: HashMap<String, ClassDefNode> = HashMap(),
  private val classParents: MutableDefaultMap<String, LinkedHashSet<String>> = MutableDefaultMap({ LinkedHashSet() }),
  private val classChildren: MutableDefaultMap<String, HashSet<String>> = MutableDefaultMap({ HashSet() }),
  private val classFields: MutableDefaultMap<String, LinkedHashMap<String, Type>> = MutableDefaultMap({ LinkedHashMap() }),
) {
  private val argsCombinationsCache: DefaultMap<List<Type>, Set<List<Type>>> = MutableDefaultMap({ args ->
    args.map {
      if (it is ClassType) orderedClassParents(it.typeName).mapTo(ArrayList(), ::ClassType)
      else arrayListOf(it)
    }.combinations()
  })
  private val classMethods: MutableDefaultMap<String, FunEnv> = MutableDefaultMap({ FunEnv(argsCombinationsCache) })
  private val functions: FunEnv = FunEnv(argsCombinationsCache, createStdLibFunEnv())

  fun classMethods(className: String): FunEnv = classMethods[className]
  fun classFields(className: String): Map<String, Type> = classFields[className]
  fun orderedClassFields(className: String): List<ClassField> =
    classFields[className].entries.map { ClassField(it.key, it.value) }

  fun addClass(classNode: ClassDefNode): Unit = with(classNode) {
    if (ident in RESERVED_IDENTIFIERS) err("Cannot define class with name $ident")
    if (ident in classes.keys) err("Redefined class with name $ident")
    classes[ident] = this
    classParents[ident] = parentClass?.let { linkedSetOf(it) } ?: linkedSetOf()
    parentClass?.let { classChildren[it] += ident }
  }

  fun addFun(funDef: FunDefNode): Unit = functions.addFun(funDef)
  operator fun get(name: String, args: List<Type>): FunDeclaration? = functions[name, args]

  fun buildClassStructure(): Unit = when (val nodes = ClassHierarchyGraph().topologicalSort()) {
    is WithCycle -> {
      val problem = nodes.cycle.map { classes[it]!! }
      val cycleNames = problem.joinToString { it.ident }
      problem.first().err("Detected cycle in class hierarchy node $cycleNames so class structure cannot be built")
    }
    is Sorted -> nodes.sorted.forEach { classes[it]!!.buildStructure() }
  }

  private fun ClassDefNode.buildStructure() {
    if (parentClass != null) classParents[ident] += classParents[parentClass]
    addFields()
    addMethods()
  }

  private fun ClassDefNode.addFields() {
    val thisClassFields = classFields[ident]
    parentClass?.let { thisClassFields += classFields[it] }
    for (field in fields) {
      val fieldName = field.ident
      if (fieldName in thisClassFields) err("Redefined field $fieldName in class or its parent class")
      thisClassFields[fieldName] = field.type
    }
  }

  private fun ClassDefNode.addMethods() {
    val parentClassMethods = parentClass?.let { classMethods[it] } ?: FunEnv(argsCombinationsCache)
    val thisClassMethods = classMethods[ident].also { it += parentClassMethods }
    for (method in methods) thisClassMethods.addFun(method, ident)
  }

  infix fun Type.isSubTypeOf(of: Type?): Boolean = when {
    this is PrimitiveType && of is PrimitiveType -> this == of
    this is ClassType && of is ClassType -> typeName == of.typeName || of.typeName in classParents[typeName]
    else -> false
  }

  fun isTypeDefined(type: Type): Boolean = when (type) {
    is PrimitiveType -> true
    NullType -> true
    is ClassType -> type.typeName in classes
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

private val RESERVED_IDENTIFIERS = buildSet {
  PrimitiveType.values().forEach { add(it.typeName) }
  add(NullType.typeName)
}

data class ClassField(val name: String, val type: Type)

private fun AstNode.err(message: String): Nothing = throw ClassHierarchyException(LocalizedMessage(message, span?.from))
