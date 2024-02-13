package com.cquilez.pitesthelper.actions

import com.cquilez.pitesthelper.model.MutationCoverageData
import com.cquilez.pitesthelper.processors.MutationCoverageCommandProcessor
import com.cquilez.pitesthelper.services.ClassService
import com.cquilez.pitesthelper.services.MyProjectService
import com.cquilez.pitesthelper.services.ServiceProvider
import com.cquilez.pitesthelper.services.UIService
import com.cquilez.pitesthelper.ui.MutationCoverageDialog
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.packageDependencies.ui.DirectoryNode
import com.intellij.pom.Navigatable
import com.intellij.psi.PsiFile
import io.mockk.*
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import java.util.*
import kotlin.test.assertNotNull
import com.intellij.openapi.module.Module

@ExtendWith(MockKExtension::class)
class RunMutationCoverageActionTest {

    @MockK
    lateinit var anActionEvent: AnActionEvent

    @MockK
    lateinit var project: Project

    @MockK
    lateinit var serviceProvider: ServiceProvider

    @MockK
    lateinit var classService: ClassService

    @MockK
    lateinit var projectService: MyProjectService

    @MockK
    lateinit var uiService: UIService

    @MockK
    lateinit var psiFile: PsiFile

    @MockK
    lateinit var directoryNode: DirectoryNode

    @MockK
    lateinit var presentation: Presentation

    @MockK
    lateinit var module: Module

    @MockK
    lateinit var navigatable: Navigatable

    @MockK
    lateinit var commandProcessor: MutationCoverageCommandProcessor

    @InjectMockKs
    lateinit var action: RunMutationCoverageAction

    @Nested
    @DisplayName("Method: update(...)")
    inner class UpdateTest {

        @Test
        fun `PsiFile is present, action is visible`() {
            every { anActionEvent.project } returns project
            every { project.service<ServiceProvider>() } returns serviceProvider
            every { serviceProvider.mockedServiceMap[ClassService::class] } returns classService
            every { anActionEvent.getData(CommonDataKeys.PSI_FILE) } returns psiFile
            every { classService.isCodeFile(psiFile) } returns true
            every { anActionEvent.presentation } returns presentation
            every { presentation.isEnabledAndVisible = true } answers {}

            action.update(anActionEvent)

            verify { presentation.isEnabledAndVisible = true }
        }

        @Test
        fun `Array is present, action is visible`() {
            every { anActionEvent.project } returns project
            every { project.service<ServiceProvider>() } returns serviceProvider
            every { serviceProvider.mockedServiceMap[ClassService::class] } returns classService
            every { anActionEvent.getData(CommonDataKeys.PSI_FILE) } returns null
            every { anActionEvent.getData(CommonDataKeys.NAVIGATABLE_ARRAY) } returns arrayOf(directoryNode)
            every { anActionEvent.presentation } returns presentation
            every { presentation.isEnabledAndVisible = true } answers {}

            action.update(anActionEvent)

            verify { presentation.isEnabledAndVisible = true }
        }

        @Test
        fun `Neither array nor class are present, action is not visible`() {
            every { anActionEvent.project } returns project
            every { project.service<ServiceProvider>() } returns serviceProvider
            every { serviceProvider.mockedServiceMap[ClassService::class] } returns classService
            every { anActionEvent.getData(CommonDataKeys.PSI_FILE) } returns null
            every { anActionEvent.getData(CommonDataKeys.NAVIGATABLE_ARRAY) } returns null
            every { anActionEvent.presentation } returns presentation
            every { presentation.isEnabledAndVisible = false } answers {}

            action.update(anActionEvent)

            verify { presentation.isEnabledAndVisible = false }
        }
    }

    @Nested
    @DisplayName("Method: actionPerformed(...)")
    inner class ActionPerformedTest {

        @Test
        fun `Invokes command processor and shows dialog with data`() {
            every { anActionEvent.project } returns project
            every { project.service<ServiceProvider>() } returns serviceProvider
            every { serviceProvider.mockedServiceMap[MyProjectService::class] } returns projectService
            every { serviceProvider.mockedServiceMap[UIService::class] } returns uiService
            every { serviceProvider.mockedServiceMap[ClassService::class] } returns classService
            val navigatableArray = arrayOf(navigatable)
            every { anActionEvent.getData(CommonDataKeys.NAVIGATABLE_ARRAY) } returns navigatableArray
            every { anActionEvent.getData(CommonDataKeys.PSI_FILE) } returns psiFile
            val mainList = Collections.emptyList<String>()
            val testList = Collections.emptyList<String>()
            val mutationCoverageData = MutationCoverageData(module, mainList, testList)
            mockkConstructor(MutationCoverageCommandProcessor::class)
            every {
                projectService.getCommandBuilder(project, projectService, classService, navigatableArray, psiFile)
            } returns commandProcessor
            every {
                commandProcessor.handleCommand()
            } answers { mutationCoverageData }

            val showDialogRun = slot<Runnable>()
            val showDialogRun2 = slot<Runnable>()
            every { uiService.showDialog(capture(showDialogRun), capture(showDialogRun2)) } answers { }

            action.actionPerformed(anActionEvent)

            assertNotNull(showDialogRun.captured)
            assertNotNull(showDialogRun2.captured)
            mockkConstructor(MutationCoverageDialog::class)
            every {
                constructedWith<MutationCoverageDialog>(EqMatcher(mutationCoverageData))
                    .show()
            } answers {}
            every {
                constructedWith<MutationCoverageDialog>(EqMatcher(mutationCoverageData))
                    .isOK
            } answers { true }
        }
    }
}