package com.cquilez.pitesthelper.actions

import com.cquilez.pitesthelper.exception.PitestHelperException
import com.cquilez.pitesthelper.model.CodeItem
import com.cquilez.pitesthelper.model.CodeItemType
import com.cquilez.pitesthelper.model.MutationCoverage
import com.cquilez.pitesthelper.services.ClassService
import com.cquilez.pitesthelper.services.MyProjectService
import com.cquilez.pitesthelper.ui.MutationCoverageData
import com.cquilez.pitesthelper.ui.MyPITestDialog
import com.intellij.ide.projectView.impl.nodes.ClassTreeNode
import com.intellij.ide.projectView.impl.nodes.PsiDirectoryNode
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleUtil
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.SourceFolder
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.pom.Navigatable
import com.intellij.psi.*
import org.jetbrains.annotations.NotNull
import org.jetbrains.annotations.TestOnly

class RunMutationCoverageAction : DumbAwareAction() {

    private val stringBuilder = StringBuilder()
    private var project: Project? = null
    private val helpMessage = "Please select only Java/Kotlin classes or packages."

    @TestOnly
    companion object {
        var mutationCoverageData : MutationCoverageData? = null
    }

    /**
     * Update action in menu.
     *
     * @param event Action event.
     */
    override fun update(@NotNull event: AnActionEvent) {
        var visible = false
        project = event.project
        if (project != null) {
            val psiFile = event.getData(CommonDataKeys.PSI_FILE)
            // TODO: Check if the directory is inside of a module and in a source root
            if (psiFile != null && ClassService.isCodeFile(psiFile)) {
                visible = true
            } else {
                val navigatableArray = event.getData(CommonDataKeys.NAVIGATABLE_ARRAY)
                if (!navigatableArray.isNullOrEmpty()) {
                    visible = true
                }
            }
        }

        event.presentation.isEnabledAndVisible = visible
    }

    override fun actionPerformed(@NotNull event: AnActionEvent) {
        val psiFile = event.getData(CommonDataKeys.PSI_FILE)

        val project = project as Project
        val projectService = project.service<MyProjectService>()

        stringBuilder.clear()
        val navigatableArray = event.getData(CommonDataKeys.NAVIGATABLE_ARRAY)

        try {
            val mutationCoverageData: MutationCoverageData =
                if (!navigatableArray.isNullOrEmpty()) {
                    processMultipleNodes(project, navigatableArray, projectService)
                } else if (psiFile != null && ClassService.isCodeFile(psiFile)) {
                    processSingleNode(psiFile)
                } else {
                    throw PitestHelperException("No elements found")
                }
            showMutationCoverageDialog(project, mutationCoverageData)
        } catch (e: PitestHelperException) {
            Messages.showErrorDialog(project, e.message, "Unable To Run Mutation Coverage")
        }
    }

    override fun getActionUpdateThread(): ActionUpdateThread {
        return ActionUpdateThread.EDT
    }

    private fun processMultipleNodes(
        project: Project,
        navigatableArray: Array<Navigatable>,
        projectService: MyProjectService
    ): MutationCoverageData {
        if (navigatableArray.isNotEmpty()) {
            checkAllElementsAreInSameModule(project, navigatableArray)
            val module = getModuleForNavigatable(project, navigatableArray[0])
            val mutationCoverage = processNavigatables(projectService, project, module, navigatableArray)
            syncClassesAndPackages(project, module, projectService, mutationCoverage)
            return buildMutationCoverageCommands(module, mutationCoverage)
        }
        throw PitestHelperException("There are no elements to process")
    }

    private fun syncClassesAndPackages(
        project: Project,
        module: Module,
        projectService: MyProjectService,
        mutationCoverage: MutationCoverage
    ) {
        val dumb = DumbService.getInstance(project).isDumb
        mutationCoverage.testSource.forEach {
            if (it.codeItemType == CodeItemType.CLASS) {
                processClassCodeItem(
                    it,
                    dumb,
                    projectService,
                    project,
                    module,
                    mutationCoverage.normalSource,
                    getClassUnderTestName(it.name)
                )
            } else if (it.codeItemType == CodeItemType.PACKAGE) {
                processPackageCodeItem(
                    project,
                    it,
                    mutationCoverage.normalSource,
                    projectService.getProductionSourceFolders(module)
                )
            }
        }
        mutationCoverage.normalSource.forEach {
            if (it.codeItemType == CodeItemType.CLASS) {
                processClassCodeItem(
                    it,
                    dumb,
                    projectService,
                    project,
                    module,
                    mutationCoverage.testSource,
                    it.name + "Test"
                )
            } else if (it.codeItemType == CodeItemType.PACKAGE) {
                processPackageCodeItem(
                    project,
                    it,
                    mutationCoverage.testSource,
                    projectService.getTestSourceFolders(module)
                )
            }
        }
    }

    private fun processPackageCodeItem(
        project: Project,
        it: CodeItem,
        sourceList: MutableList<CodeItem>,
        sourceFolderList: List<SourceFolder>
    ) {
        if (isPackageInSourceFolderList(
                project,
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

    private fun processClassCodeItem(
        it: CodeItem,
        dumb: Boolean,
        projectService: MyProjectService,
        project: Project,
        module: Module,
        sourceList: MutableList<CodeItem>,
        targetClassName: String
    ) {
        val targetClassQualifiedName: String = if (!dumb) {
            val psiClasses = projectService.findClassesInModule(targetClassName, project, module)
            // TODO: validate classes are from test source root
            checkExistingClass(psiClasses, it.qualifiedName, it.qualifiedName)
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
                                            buildFullClassName(
                                                ClassService.getPackageName(it),
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
            buildFullClassName(getPackageNameFromQualifiedName(it.qualifiedName), targetClassName)
        }
        if (!isInPackage(sourceList, targetClassName)) {
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
            val candidateClassPackageName = ClassService.getPackageName(it)
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
        project: Project,
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

    private fun buildMutationCoverageCommands(
        module: Module,
        mutationCoverage: MutationCoverage
    ): MutationCoverageData {
        return MutationCoverageData(
            module,
            buildCommand(mutationCoverage.normalSource),
            buildCommand(mutationCoverage.testSource)
        )
    }

    private fun buildCommand(
        codeItemList: List<CodeItem>
    ): String {
        val targetClassesList = mutableListOf<String>()
        codeItemList.forEach {
            if (it.codeItemType == CodeItemType.PACKAGE) {
                targetClassesList.add("${it.qualifiedName}.*")
            } else if (it.codeItemType == CodeItemType.CLASS) {
                targetClassesList.add(it.qualifiedName)
            }
        }
        return targetClassesList.sorted().joinToString(",")
    }

    private fun getPackageNameFromQualifiedName(qualifiedName: String): String {
        val lastDotIndex = qualifiedName.lastIndexOf('.')
        return if (lastDotIndex != -1) {
            qualifiedName.substring(0, lastDotIndex)
        } else ""
    }

    private fun processSingleNode(psiFile: PsiFile): MutationCoverageData {
        val psiClass = ClassService.getPublicClass(psiFile)
        val targetClasses = getQualifiedClassName(psiClass)
        val targetTests = extractTargetTestsByPsiClass(psiClass)
        val module = getModuleFromElement(psiFile)
        return MutationCoverageData(module, targetClasses, targetTests)
    }

    private fun showMutationCoverageDialog(project: Project, mutationCoverageData: MutationCoverageData) {
        if (!ApplicationManager.getApplication().isUnitTestMode) {
            val dialog = MyPITestDialog(project, mutationCoverageData.module)
            dialog.targetClasses = mutationCoverageData.targetClasses
            dialog.targetTests = mutationCoverageData.targetTests
            dialog.show()
        } else {
            RunMutationCoverageAction.mutationCoverageData = mutationCoverageData
        }
    }

    private fun getModuleForNavigatable(project: Project, navigatable: Navigatable): Module {
        val module = when (navigatable) {
            is ClassTreeNode -> {
                ModuleUtilCore.findModuleForFile((navigatable).virtualFile!!, project)
            }

            is PsiDirectoryNode -> {
                ModuleUtilCore.findModuleForFile((navigatable.value).virtualFile, project)
            }

            else -> {
                null
            }
        }
            ?: throw PitestHelperException("There is/are elements not supported. Please select only Java/Kotlin classes or packages.")
        return module
    }

    private fun checkAllElementsAreInSameModule(project: Project, navigatableArray: Array<Navigatable>) {
        if (navigatableArray.isEmpty()) {
            throw PitestHelperException("There are no elements to process")
        }
        var module: Module? = null
        navigatableArray.forEach {
            val newModule: Module = getModuleForNavigatable(project, it)
            if (module == null) {
                module = newModule
            } else if (module != newModule) {
                throw PitestHelperException("You cannot choose elements from different modules")
            }
        }
    }

    private fun getQualifiedClassName(psiClass: PsiClass): String {
        val packageName = getPackageName(psiClass)
        if (psiClass.name != null) {
            return buildFullClassName(packageName, getClassUnderTestName(psiClass.name as String))
        } else {
            throw PitestHelperException("The class name cannot be found")
        }
    }

    private fun getClassUnderTestName(testClassName: String): String = testClassName.removeSuffix("Test")

    private fun buildFullClassName(packageName: String, className: String) = "$packageName.${className}"

    private fun extractTargetTestsByPsiClass(psiClass: PsiClass): String {
        val packageName = getPackageName(psiClass)
        if (psiClass.name != null) {
            return "$packageName.${psiClass.name}"
        } else {
            throw PitestHelperException("The test class name cannot be found")
        }
    }

    private fun processNavigatables(
        projectService: MyProjectService,
        project: Project,
        module: Module,
        navigatableArray: Array<out Navigatable>
    ): MutationCoverage {
        val javaDirectoryService = JavaDirectoryService.getInstance()
        val mutationCoverage = MutationCoverage(mutableListOf(), mutableListOf())
        if (navigatableArray.isNotEmpty()) {
            navigatableArray.forEach {
                val sourceRoot: VirtualFile?
                if (it is PsiDirectoryNode) {
                    sourceRoot = projectService.getSourceRoot(project, it.virtualFile!!)
                    val javaPackage = javaDirectoryService.getPackage(it.value)
                        ?: throw PitestHelperException("The element is not a package: ${it.name}. $helpMessage")
                    addIfNotPresent(
                        projectService, module, sourceRoot, mutationCoverage,
                        CodeItem(javaPackage.name!!, javaPackage.qualifiedName, CodeItemType.PACKAGE, it)
                    )
                } else if (it is ClassTreeNode) {
                    sourceRoot = projectService.getSourceRoot(project, it.virtualFile!!)
                    val psiFile = getPsiFile(project, it)
                    if (psiFile is PsiJavaFile) {
                        val psiClass = ClassService.getPublicClass(psiFile)
                        addIfNotPresent(
                            projectService, module, sourceRoot, mutationCoverage,
                            CodeItem(psiClass.name!!, psiClass.qualifiedName!!, CodeItemType.CLASS, it)
                        )
                    }
                }
            }
        }
        return mutationCoverage
    }

    private fun addIfNotPresent(
        projectService: MyProjectService,
        module: Module,
        sourceRoot: VirtualFile?,
        mutationCoverage: MutationCoverage,
        codeItem: CodeItem
    ) {
        val codeItemList =
            if (projectService.isTestSourceRoot(module, sourceRoot!!.canonicalFile!!)) {
                mutationCoverage.testSource
            } else {
                mutationCoverage.normalSource
            }
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

    private fun getPsiFile(project: Project, classTreeNode: ClassTreeNode): PsiFile {
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

    private fun getPackageName(psiClass: PsiClass): String {
        val psiFile = psiClass.containingFile
        if (psiFile is PsiJavaFile) {
            return psiFile.packageName
        }
        throw PitestHelperException("The package name class cannot be found")
    }

    private fun getModuleFromElement(psiElement: PsiElement): Module {
        val moduleNullable = ModuleUtil.findModuleForPsiElement(psiElement)
        checkModule(moduleNullable)
        return moduleNullable as Module
    }

    private fun checkModule(module: Module?) {
        if (module == null)
            throw PitestHelperException("Module was not found!")
    }

    private fun checkExistingClass(psiClasses: Array<PsiClass>, className: String, testClassName: String) {
        if (psiClasses.isEmpty())
            throw PitestHelperException("There is no test class found for: ${className}. Test not found: ${testClassName}. A test class need to have the suffix Test.")
    }
}