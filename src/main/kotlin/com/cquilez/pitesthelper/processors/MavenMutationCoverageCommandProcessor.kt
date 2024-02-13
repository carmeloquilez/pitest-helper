package com.cquilez.pitesthelper.processors

import com.cquilez.pitesthelper.exception.PitestHelperException
import com.cquilez.pitesthelper.model.MutationCoverageData
import com.cquilez.pitesthelper.services.ClassService
import com.cquilez.pitesthelper.services.MavenService
import com.cquilez.pitesthelper.services.MyProjectService
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.pom.Navigatable
import com.intellij.psi.PsiFile

open class MavenMutationCoverageCommandProcessor(
    project: Project,
    projectService: MyProjectService,
    classService: ClassService,
    navigatableArray: Array<Navigatable>?,
    psiFile: PsiFile?
) : MutationCoverageCommandProcessor(project, projectService, classService, navigatableArray, psiFile) {

    override fun resolveModules() {
        val moduleList = if (!navigatableArray.isNullOrEmpty()) {
            listOf(projectService.getModuleForNavigatable(project, navigatableArray[0]))
        } else if (psiFile != null) {
            listOf(projectService.getModuleFromElement(psiFile))
        } else {
            throw PitestHelperException("Cannot resolve modules because Maven project info is unavailable")
        }
        mainSourceModules.addAll(moduleList)
        testSourceModules.addAll(moduleList)
    }

    override fun processProjectNodes(navigatableArray: Array<Navigatable>): MutationCoverageData {
        if (navigatableArray.size > 1) {
            checkAllElementsAreInSameModule(navigatableArray)
        }
        return super.processProjectNodes(navigatableArray)
    }

    private fun checkAllElementsAreInSameModule(navigatableArray: Array<Navigatable>) {
        if (navigatableArray.isEmpty()) {
            throw PitestHelperException("There are no elements to process")
        }
        var module: Module? = null
        navigatableArray.forEach {
            val newModule: Module = projectService.getModuleForNavigatable(project, it)
            if (module == null) {
                module = newModule
            } else if (module != newModule) {
                throw PitestHelperException("You cannot choose elements from different modules")
            }
        }
    }

    override fun buildCommand(mutationCoverageData: MutationCoverageData) =
        "mvn test-compile pitest:mutationCoverage ${
            MavenService.buildPitestArgs(
                mutationCoverageData.targetClasses.joinToString(","),
                mutationCoverageData.targetTests.joinToString(",")
            )
        }"
}