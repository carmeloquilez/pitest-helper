package com.cquilez.pitesthelper.processors

import com.cquilez.pitesthelper.exception.PitestHelperException
import com.cquilez.pitesthelper.model.MutationCoverageData
import com.cquilez.pitesthelper.services.ClassService
import com.cquilez.pitesthelper.services.MyProjectService
import com.intellij.openapi.externalSystem.model.project.ExternalSystemSourceType
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.pom.Navigatable
import com.intellij.psi.PsiFile
import org.jetbrains.plugins.gradle.model.data.GradleSourceSetData
import org.jetbrains.plugins.gradle.util.GradleUtil

open class GradleMutationCoverageCommandProcessor(
    project: Project,
    projectService: MyProjectService,
    classService: ClassService,
    navigatableArray: Array<Navigatable>?,
    psiFile: PsiFile?
) : MutationCoverageCommandProcessor(project, projectService, classService, navigatableArray, psiFile) {

    override fun resolveModules() {
        mainSourceModules.addAll(resolveModuleBySourceType(ExternalSystemSourceType.SOURCE))
        testSourceModules.addAll(resolveModuleBySourceType(ExternalSystemSourceType.TEST))
    }

    private fun resolveModuleBySourceType(sourceType: ExternalSystemSourceType): List<Module> {
        val projectDataNode = GradleUtil.findGradleModuleData(project, project.basePath!!)
            ?: throw PitestHelperException("Cannot resolve ${sourceType.name} modules because Gradle project info is unavailable")
        val sourceSets = projectDataNode.children.filter { it.data is GradleSourceSetData }.map { it.data as GradleSourceSetData }
        return sourceSets.filter { it.getCompileOutputPath(sourceType) != null }
            .map { projectService.findModuleByName(project, it.internalName) }
    }

    override fun buildCommand(mutationCoverageData: MutationCoverageData) =
        "./gradlew pitest ${
            buildPitestArgs(
                mutationCoverageData.targetClasses.joinToString(","),
                mutationCoverageData.targetTests.joinToString(",")
            )
        }"

    fun buildPitestArgs(targetClasses: String, targetTests: String) =
        "-Ppitest.targetClasses=$targetClasses -Ppitest.targetTests=$targetTests"
}