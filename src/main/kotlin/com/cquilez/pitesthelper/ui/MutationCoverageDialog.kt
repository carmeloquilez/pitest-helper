package com.cquilez.pitesthelper.ui

import com.cquilez.pitesthelper.model.MutationCoverageData
import com.intellij.openapi.observable.util.whenTextChanged
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.JBUI
import java.awt.Dimension
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.Toolkit
import javax.swing.Action
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.ScrollPaneConstants

class MutationCoverageDialog(private val mutationCoverageData: MutationCoverageData, private val commandBuilder: java.util.function.Function<MutationCoverageData, String>) : DialogWrapper(true) {
    private val tfTargetClasses = JBTextField()
    private val tfTargetTests = JBTextField()
    private val commandTextArea = JBTextArea(5, 30)
    private var rowNum = 0
    val data: Data
        get() = Data(
            tfTargetClasses.text,
            tfTargetTests.text
        )

    // Data class to work externally
    data class Data(
        val targetClasses: String,
        val targetTests: String
    )

    init {
        title = "Mutation Coverage"
        setupUiComponents()
        init()
        centerDialog()
        pack()
        centerRelativeToParent()
    }

    private fun setupUiComponents() {
        tfTargetClasses.text = mutationCoverageData.targetClasses.joinToString(",")
        tfTargetTests.text = mutationCoverageData.targetTests.joinToString(",")
        buildCommand()
    }

    private fun centerDialog() {
        setSize(
            Toolkit.getDefaultToolkit().screenSize.width / 2,
            size.height
        )
    }

    override fun createCenterPanel(): JComponent {
        val panel = JPanel(GridBagLayout())
        panel.minimumSize = Dimension(600, 200)
        val gbConstraints = GridBagConstraints()

        addRow(panel, "Target Classes", tfTargetClasses, gbConstraints)
        addRow(panel, "Target Tests", tfTargetTests, gbConstraints)

        commandTextArea.isEditable = false
        commandTextArea.lineWrap = true
        commandTextArea.margin = JBUI.insets(10)
        commandTextArea.minimumSize = Dimension()

        val scrollPane = JBScrollPane(commandTextArea)
        scrollPane.verticalScrollBarPolicy = ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS
        addRow(panel, "Maven command", scrollPane, gbConstraints, 1.0, GridBagConstraints.BOTH)

        tfTargetClasses.whenTextChanged { buildCommand() }
        tfTargetTests.whenTextChanged { buildCommand() }

        return panel
    }

    private fun addRow(
        panel: JPanel,
        fieldTitle: String,
        jbComponent: JComponent,
        gbConstraints: GridBagConstraints,
        weightY: Double? = null,
        fill: Int = GridBagConstraints.HORIZONTAL
    ) {
        gbConstraints.gridx = 0
        gbConstraints.gridy = rowNum
        gbConstraints.weightx = 0.0
        gbConstraints.fill = GridBagConstraints.NONE
        val text = if (fieldTitle.isNotBlank()) "$fieldTitle: " else ""
        panel.add(JBLabel(text), gbConstraints)

        gbConstraints.gridx++
        gbConstraints.weightx = 1.0
        weightY?.let {
            gbConstraints.weighty = weightY
        }
        gbConstraints.fill = fill
        panel.add(jbComponent, gbConstraints)

        rowNum++
    }

    /**
     * Changed OK text button by Run
     */
    override fun createActions(): Array<Action> {
        val actions: Array<Action> = super.createActions()
        for (action in actions) {
            if ("OK" == action.getValue(Action.NAME).toString()) {
                action.putValue(Action.NAME, "Run")
            }
        }
        return actions
    }

    private fun buildCommand() {
        commandTextArea.text = commandBuilder.apply(mutationCoverageData)
    }
}
