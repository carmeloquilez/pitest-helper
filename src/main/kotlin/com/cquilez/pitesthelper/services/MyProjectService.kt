package com.cquilez.pitesthelper.services

import com.cquilez.pitesthelper.MyBundle
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.rootManager
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.roots.SourceFolder
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiClass
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.PsiShortNamesCache
import org.jetbrains.jps.model.java.JavaSourceRootProperties
import org.jetbrains.jps.model.java.JavaSourceRootType


@Service(Service.Level.PROJECT)
class MyProjectService(project: Project) {

    init {
        thisLogger().info(MyBundle.message("projectService", project.name))
        thisLogger().warn("Don't forget to remove all non-needed sample code files with their corresponding registration entries in `plugin.xml`.")
    }

    fun getRandomNumber() = (1..100).random()

    fun getTestSourceFolders(module: Module): List<SourceFolder> {
        val sourceFolders = mutableListOf<SourceFolder>()
        module.rootManager.contentEntries.forEach { it ->
            sourceFolders.addAll(it.sourceFolders.filter {
                (it.rootType is JavaSourceRootType) && it.rootType.isForTests
            })
        }
        return sourceFolders
    }

    fun getProductionSourceFolders(module: Module): List<SourceFolder> {
        val sourceFolders = mutableListOf<SourceFolder>()
        module.rootManager.contentEntries.forEach { it ->
            sourceFolders.addAll(it.sourceFolders.filter {
                it.rootType is JavaSourceRootType && !it.rootType.isForTests
                        && !checkIsAutogenerated(it)
            })
        }
        return sourceFolders
    }

    fun getSourceRoot(project: Project, virtualFile: VirtualFile): VirtualFile? {
        return ProjectRootManager.getInstance(project).fileIndex.getSourceRootForFile(virtualFile)
    }

    fun isTestSourceRoot(module: Module, virtualFile: VirtualFile): Boolean {
        return getTestSourceFolders(module).any {
            it.file == virtualFile
        }
    }

    private fun checkIsAutogenerated(sourceFolder: SourceFolder): Boolean {
        if (sourceFolder.jpsElement.properties is JavaSourceRootProperties) {
            return (sourceFolder.jpsElement.properties as JavaSourceRootProperties).isForGeneratedSources
        }
        return false
    }

    fun findClassesInModule(className: String, project: Project, module: Module): Array<PsiClass> {
        val scope = GlobalSearchScope.moduleScope(module)
        return PsiShortNamesCache.getInstance(project).getClassesByName(className, scope)
    }
}
