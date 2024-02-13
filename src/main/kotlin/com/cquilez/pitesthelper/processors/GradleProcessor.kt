package com.cquilez.pitesthelper.processors

import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project

@Service(Service.Level.PROJECT)
class GradleProcessor : Processor() {
    override fun processProjectNodes(project: Project) {
    }
}