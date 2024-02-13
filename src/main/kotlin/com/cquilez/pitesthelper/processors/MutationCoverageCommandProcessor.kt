package com.cquilez.pitesthelper.processors

import com.cquilez.pitesthelper.exception.PitestHelperException
import com.cquilez.pitesthelper.model.CodeItem
import com.cquilez.pitesthelper.model.CodeItemType
import com.cquilez.pitesthelper.model.MutationCoverage
import com.cquilez.pitesthelper.model.MutationCoverageData
import com.cquilez.pitesthelper.services.*
import com.intellij.ide.projectView.impl.nodes.ClassTreeNode
import com.intellij.ide.projectView.impl.nodes.PsiDirectoryNode
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.SourceFolder
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.pom.Navigatable
import com.intellij.psi.*
import org.jetbrains.jps.model.java.JavaSourceRootType

abstract class MutationCoverageCommandProcessor(
    val project: Project,
    val projectService: MyProjectService,
    private val classService: ClassService,
    val navigatableArray: Array<Navigatable>?,
    val psiFile: PsiFile?
) {
    private val helpMessage = "Please select only Java classes, packages or a module folder containing Java source code."

    protected val mainSourceModules = mutableListOf<Module>()
    protected val testSourceModules = mutableListOf<Module>()

    fun handleCommand(): MutationCoverageData {
        resolveModules()
        val mutationCoverageData: MutationCoverageData =
            if (navigatableArray != null) {
                readMultipleNodes(navigatableArray)
            } else if (psiFile != null && classService.isCodeFile(psiFile)) {
                readSingleNode(psiFile)
            } else {
                throw PitestHelperException("No elements found")
            }
        return mutationCoverageData
    }

    protected abstract fun resolveModules()
    abstract fun buildCommand(mutationCoverageData: MutationCoverageData) : String

    private fun readMultipleNodes(
        navigatableArray: Array<Navigatable>
    ): MutationCoverageData {
        if (navigatableArray.isNotEmpty()) {
            return processProjectNodes(navigatableArray)
        }
        throw PitestHelperException("There are no elements to process")
    }

    protected open fun processProjectNodes(navigatableArray: Array<Navigatable>): MutationCoverageData {
        val module = projectService.getModuleForNavigatable(project, navigatableArray[0])
        val mutationCoverage = processNavigatables(module, navigatableArray)
        syncClassesAndPackages(module, mutationCoverage)
        return PITestService.buildMutationCoverageCommand(module, mutationCoverage)
    }

    private fun syncClassesAndPackages(
        module: Module,
        mutationCoverage: MutationCoverage
    ) {
        val dumb = DumbService.getInstance(project).isDumb
        mutationCoverage.testSource.forEach {
            if (it.codeItemType == CodeItemType.CLASS) {
                findCandidateClass(
                    it,
                    dumb,
                    module,
                    mutationCoverage.normalSource,
                    TestService.getClassUnderTestName(it.name),
                    mainSourceModules
                )
            } else if (it.codeItemType == CodeItemType.PACKAGE) {
                processCandidatePackage(
                    it,
                    mutationCoverage.normalSource,
                    projectService.getMainSourceFolders(mainSourceModules)
                )
            }
        }
        mutationCoverage.normalSource.forEach {
            if (it.codeItemType == CodeItemType.CLASS) {
                findCandidateClass(
                    it,
                    dumb,
                    module,
                    mutationCoverage.testSource,
                    it.name + "Test",
                    testSourceModules
                )
            } else if (it.codeItemType == CodeItemType.PACKAGE) {
                processCandidatePackage(
                    it,
                    mutationCoverage.testSource,
                    projectService.getTestSourceFolders(testSourceModules)
                )
            }
        }
    }

    private fun processCandidatePackage(
        it: CodeItem,
        sourceList: MutableList<CodeItem>,
        sourceFolderList: List<SourceFolder>
    ) {
        if (isPackageInSourceFolderList(
                it.qualifiedName,
                sourceFolderList
            )
        ) {
            if (!isInPackage(sourceList, it.qualifiedName)) {
                sourceList.add(it)
            }
        } else {
            throw PitestHelperException("Package not found!: ${it.qualifiedName}")
        }
    }

    private fun findCandidateClass(
        it: CodeItem,
        dumb: Boolean,
        module: Module,
        sourceList: MutableList<CodeItem>,
        targetClassName: String,
        targetSourceModules: List<Module>
    ) {
        val targetClassQualifiedName: String = if (!dumb) {
            val psiClasses = projectService.findClassesInModules(targetClassName, project, targetSourceModules)
            checkExistingClass(psiClasses, it.qualifiedName, targetClassName)
            val psiClass = if (psiClasses.size == 1) {
                psiClasses[0]
            } else {
                var psiClass =
                    findClassInSamePackage(psiClasses, getPackageNameFromQualifiedName(it.qualifiedName))
                if (psiClass == null) {
                    psiClass =
                        findBestCandidateClass(psiClasses, getPackageNameFromQualifiedName(it.qualifiedName))
                }
                if (psiClass == null) {
                    throw PitestHelperException(
                        "Class under test is not in the same package. Unable to find valid class. " +
                                "Candidates are: ${
                                    psiClasses.joinToString(", ",
                                        transform = {
                                            PITestService.buildFullClassName(
                                                classService.getPackageName(it),
                                                it.name!!
                                            )
                                        })
                                }"
                    )
                }
                psiClass
            }
            psiClass.qualifiedName!!
        } else {
            PITestService.buildFullClassName(getPackageNameFromQualifiedName(it.qualifiedName), targetClassName)
        }
        if (!isInPackage(sourceList, targetClassQualifiedName)) {
            sourceList.add(
                CodeItem(
                    targetClassName,
                    targetClassQualifiedName,
                    CodeItemType.CLASS,
                    it.navigatable
                )
            )
        }
    }

    private fun findBestCandidateClass(psiClasses: Array<PsiClass>, classQualifiedName: String): PsiClass? {
        var psiClass: PsiClass? = null
        psiClasses.forEach {
            val candidateClassPackageName = classService.getPackageName(it)
            if (PsiNameHelper.isSubpackageOf(classQualifiedName, candidateClassPackageName)
                || PsiNameHelper.isSubpackageOf(candidateClassPackageName, classQualifiedName)
            ) {
                if (psiClass == null) {
                    psiClass = it
                } else {
                    return null
                }
            }
        }
        return psiClass
    }

    private fun isPackageInSourceFolderList(
        qualifiedName: String,
        sourceFolderList: List<SourceFolder>
    ): Boolean {
        val psiManager = PsiManager.getInstance(project)
        for (sourceRoot in sourceFolderList) {
            val packageDirectory = sourceRoot.file!!.findFileByRelativePath(qualifiedName.replace('.', '/'))
            if (packageDirectory != null && packageDirectory.isDirectory) {
                val psiDirectory = psiManager.findDirectory(packageDirectory)
                if (psiDirectory != null) {
                    val psiPackage = JavaDirectoryService.getInstance().getPackage(psiDirectory)
                    if (psiPackage != null && psiPackage.qualifiedName == qualifiedName) {
                        return true
                    }
                }
            }
        }
        return false
    }

    private fun getPackageNameFromQualifiedName(qualifiedName: String): String {
        val lastDotIndex = qualifiedName.lastIndexOf('.')
        return if (lastDotIndex != -1) {
            qualifiedName.substring(0, lastDotIndex)
        } else ""
    }

    private fun readSingleNode(psiFile: PsiFile): MutationCoverageData {
        val psiClass = classService.getPublicClass(psiFile)
        val targetClasses = PITestService.getTestClassQualifiedName(psiClass)
        val targetTests = PITestService.extractTargetTestsByPsiClass(psiClass)
        val module = projectService.getModuleFromElement(psiFile)
        return MutationCoverageData(module, listOf(targetClasses), listOf(targetTests))
    }

    private fun processNavigatables(
        module: Module,
        navigatableArray: Array<out Navigatable>
    ): MutationCoverage {
        val javaDirectoryService = JavaDirectoryService.getInstance()
        val mutationCoverage = MutationCoverage(mutableListOf(), mutableListOf())
        if (navigatableArray.isNotEmpty()) {
            navigatableArray.forEach { navigatable ->
                processNavigatable(navigatable, javaDirectoryService, module, mutationCoverage)
            }
        }
        return mutationCoverage
    }

    private fun processNavigatable(
        navigatable: Navigatable,
        javaDirectoryService: JavaDirectoryService,
        module: Module,
        mutationCoverage: MutationCoverage
    ) {
        if (navigatable is PsiDirectoryNode) {
            processDirectory(javaDirectoryService, navigatable, module, mutationCoverage)
        } else if (navigatable is ClassTreeNode) {
            processClass(navigatable, module, mutationCoverage)
        }
    }

    protected open fun processClass(
        navigatable: ClassTreeNode,
        module: Module,
        mutationCoverage: MutationCoverage
    ) {
        val sourceRoot: VirtualFile? = projectService.getSourceRoot(project, navigatable.virtualFile!!)
        val psiFile = getPsiFile(navigatable)
        if (psiFile is PsiJavaFile) {
            val psiClass = classService.getPublicClass(psiFile)
            addIfNotPresent(
                module, sourceRoot!!, mutationCoverage,
                CodeItem(psiClass.name!!, psiClass.qualifiedName!!, CodeItemType.CLASS, navigatable)
            )
        }
    }

    protected open fun processDirectory(
        javaDirectoryService: JavaDirectoryService,
        psiDirectoryNode: PsiDirectoryNode,
        module: Module,
        mutationCoverage: MutationCoverage
    ) {
        val sourceRoot: VirtualFile?
        val packageName: String
        var qualifiedName: String
        val javaPackage = javaDirectoryService.getPackage(psiDirectoryNode.value)
        if (javaPackage != null && javaPackage.name != null && javaPackage.name!!.isNotBlank()) {
            packageName = javaPackage.name!!
            qualifiedName = javaPackage.qualifiedName
            if (qualifiedName == "") {
                qualifiedName = packageName
            }
            sourceRoot = projectService.getSourceRoot(project, psiDirectoryNode.virtualFile!!)
            addIfNotPresent(
                module, sourceRoot!!, mutationCoverage,
                CodeItem(packageName, qualifiedName, CodeItemType.PACKAGE, psiDirectoryNode)
            )
        } else {
            val sourceFolders = projectService.getFilteredSourceFolders(module) {
                it.rootType is JavaSourceRootType && !projectService.isAutogeneratedSourceFolder(it)
            }
            val directoryPath = psiDirectoryNode.virtualFile?.toNioPath()
            if (directoryPath != null) {
                val sourceFolderFound = sourceFolders.stream().filter {
                    it.file?.toNioPath()?.startsWith(directoryPath) ?: false
                }.findFirst()
                if (sourceFolderFound.isPresent && sourceFolderFound.get().file != null) {
                    packageName = getBasePackage(sourceFolderFound.get().file!!)
                    diffElements(mutationCoverage.normalSource, CodeItem(packageName, packageName, CodeItemType.PACKAGE, psiDirectoryNode))
                } else {
                    throw PitestHelperException("The directory appears to contain no code. $helpMessage")
                }
            } else {
                throw PitestHelperException("The directory appears to contain no code. $helpMessage")
            }
        }
    }

    private fun getBasePackage(rootFile: VirtualFile): String {
        var lastNode = rootFile
        while (lastNode.children.size == 1) {
            lastNode = lastNode.children[0]
        }
        val relativePath = VfsUtil.getRelativePath(lastNode, rootFile)
        return relativePath?.replace("/", ".") ?: ""
    }

    private fun addIfNotPresent(
        module: Module,
        sourceRoot: VirtualFile,
        mutationCoverage: MutationCoverage,
        codeItem: CodeItem
    ) {
        val codeItemList =
            if (projectService.isTestSourceRoot(module, sourceRoot.canonicalFile!!)) {
                mutationCoverage.testSource
            } else {
                mutationCoverage.normalSource
            }
        diffElements(codeItemList, codeItem)
    }

    private fun diffElements(
        codeItemList: MutableList<CodeItem>,
        codeItem: CodeItem
    ) {
        if (!isInPackage(codeItemList, codeItem.qualifiedName)) {
            val descendantCodeList = getChildCode(codeItemList, codeItem.qualifiedName)
            codeItemList.removeAll(descendantCodeList)
            codeItemList.add(codeItem)
        }
    }

    private fun getChildCode(codeItemList: MutableList<CodeItem>, newItemQualifiedName: String): List<CodeItem> {
        return codeItemList.filter {
            PsiNameHelper.isSubpackageOf(it.qualifiedName, newItemQualifiedName)
        }
    }

    private fun isInPackage(
        codeItemList: List<CodeItem>,
        newItemPackage: String
    ): Boolean {
        codeItemList.forEach {
            val existingItemPackage = it.qualifiedName
            if (PsiNameHelper.isSubpackageOf(newItemPackage, existingItemPackage)) {
                return true
            }
        }
        return false
    }

    protected fun getPsiFile(classTreeNode: ClassTreeNode): PsiFile {
        val psiManager = PsiManager.getInstance(project)
        val virtualFile = classTreeNode.virtualFile
        if (virtualFile != null) {
            val psiFile = psiManager.findFile(virtualFile)
            if (psiFile != null) {
                return psiFile
            }
        }
        throw PitestHelperException("There are selected elements not supported: ${classTreeNode.name}. $helpMessage")
    }

    private fun findClassInSamePackage(psiClasses: Array<PsiClass>, packageName: String): PsiClass? {
        return psiClasses.firstOrNull {
            val psiFile = it.containingFile
            if (psiFile is PsiJavaFile) {
                psiFile.packageName == packageName
            } else {
                false
            }
        }
    }

    private fun checkExistingClass(psiClasses: Array<PsiClass>, className: String, testClassName: String) {
        if (psiClasses.isEmpty())
            throw PitestHelperException("There is no test class found for: ${className}. Searched class: ${testClassName}. A test class need to have the suffix Test.")
    }
}