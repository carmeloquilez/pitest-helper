package com.cquilez.pitesthelper.services

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import kotlin.reflect.KClass

/**
 * Manages UI
 */
@Service(Service.Level.PROJECT)
class ServiceProvider {

    val mockedServiceMap : MutableMap<KClass<out Any>, Any> = mutableMapOf()

    inline fun <reified T : Any>getService(project: Project): T {
        return if (ApplicationManager.getApplication() != null && !ApplicationManager.getApplication().isUnitTestMode) {
            project.service<T>()
        } else {
            mockedServiceMap[T::class] as T? ?: project.service<T>()
        }
    }
}