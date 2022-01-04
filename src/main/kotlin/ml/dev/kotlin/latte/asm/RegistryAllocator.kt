package ml.dev.kotlin.latte.asm

//import ml.dev.kotlin.latte.quadruple.LocalFlowAnalysis
//import ml.dev.kotlin.latte.quadruple.StmtIdx
//import ml.dev.kotlin.latte.quadruple.VirtualReg
//
//class RegistryAllocator(
//  private val flowAnalysis: LocalFlowAnalysis,
//  private val statementsCount: Int,
//) {
//
//  init {
//    val firstDefined = hashMapOf<VirtualReg, StmtIdx>()
//    val lastUsed = hashMapOf<VirtualReg, StmtIdx>()
//
//    for (idx in 0 until statementsCount) {
//      flowAnalysis.definedAt[idx].forEach { if (it !in firstDefined) firstDefined[it] = idx }
//      flowAnalysis.usedAt[idx].forEach { lastUsed[it] = idx }
//    }
//
//  }
//}

