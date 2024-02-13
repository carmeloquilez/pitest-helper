package com.cquilez.pitesthelper.processors

import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project

@Service(Service.Level.PROJECT)
class MavenProcessor : Processor() {

    override fun processProjectNodes(project: Project) {
    }
}