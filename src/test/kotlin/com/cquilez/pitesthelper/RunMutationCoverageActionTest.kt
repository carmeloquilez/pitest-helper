package com.cquilez.pitesthelper

import com.cquilez.pitesthelper.actions.RunMutationCoverageAction
import com.cquilez.pitesthelper.services.ClassService
import com.intellij.ide.projectView.impl.nodes.ClassTreeNode
import com.intellij.openapi.actionSystem.CommonDataKeys.NAVIGATABLE_ARRAY
import com.intellij.openapi.actionSystem.CommonDataKeys.PROJECT
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.impl.ModifiableModelCommitter
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.testFramework.TestActionEvent
import com.intellij.testFramework.builders.JavaModuleFixtureBuilder
import com.intellij.testFramework.fixtures.IdeaTestFixtureFactory
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture
import com.intellij.testFramework.fixtures.JavaTestFixtureFactory
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import java.nio.file.Paths

/**
 * Tests Run mutation coverage action tests
 */
class RunMutationCoverageActionTest {

    companion object {

        private lateinit var fixture: JavaCodeInsightTestFixture

        @BeforeAll
        @JvmStatic
        internal fun beforeAll() {
            val projectBuilder = IdeaTestFixtureFactory.getFixtureFactory().createFixtureBuilder("MyFixtureBuilder")
            fixture = JavaTestFixtureFactory.getFixtureFactory().createCodeInsightFixture(projectBuilder.fixture)
            fixture.testDataPath = "./src/test/testData"
            val builder1 = projectBuilder.addModule(JavaModuleFixtureBuilder::class.java)
            builder1
                .addContentRoot(fixture.tempDirPath)
                .addSourceRoot("src/main/java")

            fixture.setUp()
            fixture.copyDirectoryToProject("sampleProject", "")

            // Add test SourceRoot in src/test/ava
            val module = ModuleManager.getInstance(fixture.project).modules[0]
            val directory = LocalFileSystem.getInstance().findFileByIoFile(Paths.get(fixture.tempDirPath).resolve("src/test/java").toFile())
            val rootModel = ModuleRootManager.getInstance(module).modifiableModel

            if (directory != null) {
                rootModel.contentEntries[0].addSourceFolder(directory, true)
            } else {
                fail("Test source root could not be configured")
            }
            val moduleModel = ModuleManager.getInstance(module.project).getModifiableModel()
            ApplicationManager.getApplication().invokeLater() {
                ApplicationManager.getApplication().runWriteAction() {
                    ModifiableModelCommitter.multiCommit(arrayOf(rootModel), moduleModel)
                }
            }
        }

        @AfterAll
        @JvmStatic
        internal fun afterAll() {
            fixture.tearDown()
        }
    }

    @Test
    fun `If no classes or packages selected, action not visible`() {
        ApplicationManager.getApplication().runReadAction {
            val dataContext = DataContext {
                return@DataContext null
            }

            val event = TestActionEvent(dataContext)
            val action = RunMutationCoverageAction()

            action.update(event)
            assertFalse(event.presentation.isVisible)
        }
    }

    @Test
    fun `Single Main Class selected and Test Class exists, single target class and single test class`() {
        val psiFile = fixture.configureFromTempProjectFile("src/main/java/com/myproject/ClassA.java")

        ApplicationManager.getApplication().runReadAction {
            val dataContext = DataContext {
                when (it) {
                    PROJECT.name -> {
                        return@DataContext fixture.project
                    }

                    NAVIGATABLE_ARRAY.name -> {
                        val classTreeNode = ClassTreeNode(fixture.project, ClassService.getPublicClass(psiFile), null)
                        return@DataContext arrayOf(classTreeNode)
                    }

                    else -> {
                        return@DataContext null
                    }
                }
            }

            val event = TestActionEvent(dataContext)
            val action = RunMutationCoverageAction()

            action.update(event)
            assertTrue(event.presentation.isVisible)

            action.actionPerformed(event)

            val data = RunMutationCoverageAction.mutationCoverageData
            assertTrue(data != null)
            assertTrue(data!!.targetClasses.isNotEmpty())
            assertEquals("com.myproject.ClassA", data.targetClasses)
            assertTrue(data.targetTests.isNotEmpty())
            assertEquals("com.myproject.ClassATest", data.targetTests)
        }
    }

    @Test
    fun `Single Test Class selected and Main Class exists, single target class and single test class`() {
        val psiFile = fixture.configureFromTempProjectFile("src/test/java/com/myproject/ClassATest.java")

        ApplicationManager.getApplication().runReadAction {
            val dataContext = DataContext {
                when (it) {
                    PROJECT.name -> {
                        return@DataContext fixture.project
                    }

                    NAVIGATABLE_ARRAY.name -> {
                        val classTreeNode = ClassTreeNode(fixture.project, ClassService.getPublicClass(psiFile), null)
                        return@DataContext arrayOf(classTreeNode)
                    }

                    else -> {
                        return@DataContext null
                    }
                }
            }

            val event = TestActionEvent(dataContext)
            val action = RunMutationCoverageAction()

            action.update(event)
            assertTrue(event.presentation.isVisible)

            action.actionPerformed(event)

            val data = RunMutationCoverageAction.mutationCoverageData
            assertTrue(data != null)
            assertTrue(data!!.targetClasses.isNotEmpty())
            assertEquals("com.myproject.ClassA", data.targetClasses)
            assertTrue(data.targetTests.isNotEmpty())
            assertEquals("com.myproject.ClassATest", data.targetTests)
        }
    }
}