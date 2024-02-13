package com.cquilez.pitesthelper.mutationcoverage

import com.cquilez.pitesthelper.actions.RunMutationCoverageAction
import com.cquilez.pitesthelper.common.TestUtil
import com.cquilez.pitesthelper.common.TestUtil.Companion.createDataContext
import com.cquilez.pitesthelper.common.TestUtil.Companion.findAndCreateDirectoryTreeNode
import com.cquilez.pitesthelper.common.TestUtil.Companion.newClassTreeNode
import com.cquilez.pitesthelper.model.MutationCoverageData
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.application.ApplicationManager
import com.intellij.testFramework.TestActionEvent
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test

/**
 * Tests Run mutation coverage action when a single node is selected
 */
class MutationCoverageSingleNodeTest {

    companion object {

        private lateinit var fixture: JavaCodeInsightTestFixture

        @BeforeAll
        @JvmStatic
        internal fun beforeAll() {
            val projectBuilder = TestUtil.createFixtureBuilder()
            fixture = TestUtil.loadTestProject(projectBuilder, "sampleProject")
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
                when (it) {
                    CommonDataKeys.PROJECT.name -> {
                        return@DataContext fixture.project
                    }

                    else -> {
                        return@DataContext null
                    }
                }
            }
            val event = TestActionEvent(dataContext)
            val action = RunMutationCoverageAction()

            action.update(event)
            assertFalse(event.presentation.isVisible)
        }
    }

    @Test
    fun `Single Main Class selected and Test Class exists, single target class and single test class`() {
        val classA = fixture.configureFromTempProjectFile( "src/main/java/com/myproject/package1/ClassA.java")
        ApplicationManager.getApplication().runReadAction {
            val dataContext = createDataContext(fixture, arrayOf(newClassTreeNode(fixture.project, classA)))
            val event = TestActionEvent(dataContext)
            val action = RunMutationCoverageAction()

            action.update(event)
            assertTrue(event.presentation.isVisible)

            action.actionPerformed(event)

            val data = RunMutationCoverageAction.mutationCoverageData as MutationCoverageData
            assertThat(data.targetClasses, Matchers.contains("com.myproject.package1.ClassA"))
            assertThat(data.targetTests, Matchers.contains("com.myproject.package1.ClassATest"))
        }
    }

    @Test
    fun `Single Main Package selected and Test Package exists, single target package and single test package`() {
        ApplicationManager.getApplication().runReadAction {
            val directoryTreeNode = findAndCreateDirectoryTreeNode(fixture, "src/main/java/com/myproject/package2")
            val dataContext = createDataContext(fixture, arrayOf(directoryTreeNode))
            val event = TestActionEvent(dataContext)
            val action = RunMutationCoverageAction()

            action.update(event)
            assertTrue(event.presentation.isVisible)

            action.actionPerformed(event)

            val data = RunMutationCoverageAction.mutationCoverageData as MutationCoverageData
            assertThat(data.targetClasses, Matchers.contains("com.myproject.package2.*"))
            assertThat(data.targetTests, Matchers.contains("com.myproject.package2.*"))
        }
    }

    @Test
    fun `Single Test Class selected and Main Class exists, single target class and single test class`() {
        val psiFile = fixture.configureFromTempProjectFile("src/test/java/com/myproject/package2/ClassBTest.java")
        ApplicationManager.getApplication().runReadAction {
            val dataContext = createDataContext(fixture, arrayOf(newClassTreeNode(fixture.project, psiFile)))

            val event = TestActionEvent(dataContext)
            val action = RunMutationCoverageAction()

            action.update(event)
            assertTrue(event.presentation.isVisible)

            action.actionPerformed(event)

            val data = RunMutationCoverageAction.mutationCoverageData as MutationCoverageData
            assertThat(data.targetClasses, Matchers.contains("com.myproject.package2.ClassB"))
            assertThat(data.targetTests, Matchers.contains("com.myproject.package2.ClassBTest"))
        }
    }

    @Test
    fun `Single Test Package selected and Main Package exists, single target package and single test package`() {
        ApplicationManager.getApplication().runReadAction {
            val directoryTreeNode = findAndCreateDirectoryTreeNode(fixture, "src/test/java/com/myproject/package1")
            val dataContext = createDataContext(fixture, arrayOf(directoryTreeNode))
            val event = TestActionEvent(dataContext)
            val action = RunMutationCoverageAction()

            action.update(event)
            assertTrue(event.presentation.isVisible)

            action.actionPerformed(event)

            val data = RunMutationCoverageAction.mutationCoverageData as MutationCoverageData
            assertThat(data.targetClasses, Matchers.contains("com.myproject.package1.*"))
            assertThat(data.targetTests, Matchers.contains("com.myproject.package1.*"))
        }
    }
}