package com.cquilez.pitesthelper.services

import com.cquilez.pitesthelper.exception.PitestHelperException
import com.intellij.openapi.components.Service
import com.intellij.psi.*

@Service(Service.Level.PROJECT)
class ClassService {
    fun getPublicClass(psiFile: PsiFile): PsiClass {
        if (psiFile is PsiJavaFile) {
            val psiClasses: Array<PsiClass> = psiFile.classes
            return psiClasses.first { service -> service.isPhysical }
        }
        throw PitestHelperException("Invalid class")
    }

    fun isCodeFile(psiFile: PsiFile): Boolean {
        return psiFile is PsiJavaFile
    }

    fun getPackageName(psiClass: PsiClass): String {
        val psiFile = psiClass.containingFile
        if (psiFile is PsiJavaFile) {
            return psiFile.packageName
        } else {
            throw PitestHelperException("Invalid language")
        }
    }
}