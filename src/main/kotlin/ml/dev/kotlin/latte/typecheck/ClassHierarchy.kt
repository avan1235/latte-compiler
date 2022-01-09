package ml.dev.kotlin.latte.typecheck

import ml.dev.kotlin.latte.syntax.*
import ml.dev.kotlin.latte.util.*

data class ClassHierarchy(
  private val classes: HashMap<String, ClassDefNode> = HashMap(),
  private val classParents: MutableDefaultMap<String, LinkedHashSet<String>> = MutableDefaultMap({ LinkedHashSet() }),
  private val classChildren: MutableDefaultMap<String, HashSet<String>> = MutableDefaultMap({ HashSet() }),
  private val classFields: MutableDefaultMap<String, LinkedHashMap<String, Type>> = MutableDefaultMap({ LinkedHashMap() }),
  private val classMethods: MutableDefaultMap<String, HashMap<String, Type>> = MutableDefaultMap({ HashMap() }),
) {
  fun classMethods(className: String): Map<String, Type> = classMethods[className]
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
    val parentClassMethods = parentClass?.let { classMethods[it] } ?: HashMap()
    val thisClassMethods = classMethods[ident].also { it += parentClassMethods }
    for (method in methods) {
      val methodName = method.ident mangled method.args.list.map { it.type }
      if (methodName in thisClassMethods && methodName !in parentClassMethods) err("Redefined method ${method.ident}")
      thisClassMethods[methodName] = method.type // TODO check if this type matches type from parent class
      method.mangledName = methodName
    }
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
