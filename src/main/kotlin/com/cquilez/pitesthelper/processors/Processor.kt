package com.cquilez.pitesthelper.processors

import com.intellij.openapi.project.Project

abstract class Processor {
    abstract fun processProjectNodes(project: Project)
}