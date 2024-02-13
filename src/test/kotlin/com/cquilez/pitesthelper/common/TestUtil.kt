package com.cquilez.pitesthelper.common

import com.cquilez.pitesthelper.exception.PitestHelperException
import com.intellij.ide.projectView.impl.nodes.ClassTreeNode
import com.intellij.ide.projectView.impl.nodes.PsiDirectoryNode
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.impl.ModifiableModelCommitter
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.pom.Navigatable
import com.intellij.psi.*
import com.intellij.testFramework.builders.JavaModuleFixtureBuilder
import com.intellij.testFramework.fixtures.*
import org.junit.jupiter.api.fail
import java.nio.file.Paths

class TestUtil {

    companion object {
        fun createFixtureBuilder() = IdeaTestFixtureFactory.getFixtureFactory().createFixtureBuilder("MyFixtureBuilder")

        fun loadTestProject(projectBuilder: TestFixtureBuilder<IdeaProjectTestFixture>, relativePath: String): JavaCodeInsightTestFixture {
            val fixture = JavaTestFixtureFactory.getFixtureFactory().createCodeInsightFixture(projectBuilder.fixture)
            fixture.testDataPath = "./src/test/testData"
            val builder1 = projectBuilder.addModule(JavaModuleFixtureBuilder::class.java)
            builder1
                .addContentRoot(fixture.tempDirPath)
                .addSourceRoot("src/main/java")

            fixture.setUp()
            fixture.copyDirectoryToProject(relativePath, "")

            val module = ModuleManager.getInstance(fixture.project).modules[0]
            val directory = LocalFileSystem.getInstance().findFileByIoFile(Paths.get(fixture.tempDirPath).resolve("src/test/java").toFile())
            val rootModel = ModuleRootManager.getInstance(module).modifiableModel

            if (directory != null) {
                rootModel.contentEntries[0].addSourceFolder(directory, true)
            } else {
                fail("Test source root could not be configured")
            }
            val moduleModel = ModuleManager.getInstance(module.project).getModifiableModel()
            ApplicationManager.getApplication().invokeLater {
                ApplicationManager.getApplication().runWriteAction {
                    ModifiableModelCommitter.multiCommit(arrayOf(rootModel), moduleModel)
                }
            }
            return fixture
        }

        fun createDataContext(fixture: JavaCodeInsightTestFixture, navigableArray: Array<out Navigatable>): DataContext {
            return DataContext {
                when (it) {
                    CommonDataKeys.PROJECT.name -> {
                        return@DataContext fixture.project
                    }

                    CommonDataKeys.NAVIGATABLE_ARRAY.name -> {
                        return@DataContext navigableArray
                    }

                    else -> {
                        return@DataContext null
                    }
                }
            }
        }

        private fun getPublicClass(psiFile: PsiFile): PsiClass {
            if (psiFile is PsiJavaFile) {
                val psiClasses: Array<PsiClass> = psiFile.classes
                return psiClasses.first { service -> service.isPhysical }
            }
            throw PitestHelperException("Invalid class")
        }

        fun newClassTreeNode(project: Project, psiFile: PsiFile) = ClassTreeNode(project, getPublicClass(psiFile), null)

        fun findAndCreateDirectoryTreeNode(fixture: JavaCodeInsightTestFixture, relativePath: String): PsiDirectoryNode {
            val module = ModuleManager.getInstance(fixture.project).modules[0]
            val packageVirtualFile = ModuleRootManager.getInstance(module).contentRoots[0].findFileByRelativePath(relativePath)
            if (packageVirtualFile == null || !packageVirtualFile.isDirectory) {
                fail("Fail preparing test: Package com/project was not found")
            }
            val psiManager = PsiManager.getInstance(fixture.project)
            var psiDirectory: PsiDirectory? = null
            ApplicationManager.getApplication().runReadAction {
                psiDirectory = psiManager.findDirectory(packageVirtualFile)
                if (psiDirectory == null || !psiDirectory!!.isDirectory) {
                    fail("Fail preparing test: PsiDirectory com/project was not found")
                }
            }
            return PsiDirectoryNode(fixture.project, psiDirectory!!, null)
        }
    }
}