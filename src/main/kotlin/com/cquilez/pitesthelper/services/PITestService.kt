package com.cquilez.pitesthelper.services

import com.cquilez.pitesthelper.exception.PitestHelperException
import com.cquilez.pitesthelper.model.CodeItem
import com.cquilez.pitesthelper.model.CodeItemType
import com.cquilez.pitesthelper.model.MutationCoverage
import com.cquilez.pitesthelper.model.MutationCoverageData
import com.intellij.openapi.module.Module
import com.intellij.psi.PsiClass

object PITestService {

    /**
     * Build mutation coverage command data
     */
    fun buildMutationCoverageCommand(
        module: Module,
        mutationCoverage: MutationCoverage
    ): MutationCoverageData {
        return MutationCoverageData(
            module,
            collectTargetCode(mutationCoverage.normalSource),
            collectTargetCode(mutationCoverage.testSource)
        )
    }

    /**
     * Collects target packages and classes in a List
     */
    fun collectTargetCode(
        codeItemList: List<CodeItem>
    ): List<String> {
        val targetClassesList = mutableListOf<String>()
        codeItemList.forEach {
            if (it.codeItemType == CodeItemType.PACKAGE) {
                targetClassesList.add("${it.qualifiedName}.*")
            } else if (it.codeItemType == CodeItemType.CLASS) {
                targetClassesList.add(it.qualifiedName)
            }
        }
        return targetClassesList.sorted()
    }

    fun getTestClassQualifiedName(psiClass: PsiClass): String {
        val packageName = PsiService.getPackageName(psiClass)
        if (psiClass.name != null) {
            return buildFullClassName(packageName, TestService.getClassUnderTestName(psiClass.name as String))
        } else {
            throw PitestHelperException("The class name cannot be found")
        }
    }

    fun extractTargetTestsByPsiClass(psiClass: PsiClass): String {
        val packageName = PsiService.getPackageName(psiClass)
        if (psiClass.name != null) {
            return "$packageName.${psiClass.name}"
        } else {
            throw PitestHelperException("The test class name cannot be found")
        }
    }

    fun buildFullClassName(packageName: String, className: String) = "$packageName.${className}"
}
