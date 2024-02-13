package com.cquilez.pitesthelper.services

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service

/**
 * Manages UI
 */
@Service(Service.Level.PROJECT)
class UIService {

    /**
     * Show dialog only if unit test mode is disabled
     */
    fun showDialog(mainAction: Runnable){
        if (!ApplicationManager.getApplication().isUnitTestMode) {
            mainAction.run()
        }
    }

    /**
     * Show dialog only if unit test mode is disabled or execute orElseAction
     */
    fun showDialog(mainAction: Runnable, orElseAction: Runnable){
        if (!ApplicationManager.getApplication().isUnitTestMode) {
            mainAction.run()
        } else {
            orElseAction.run()
        }
    }
}
