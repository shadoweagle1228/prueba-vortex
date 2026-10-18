package com.andesretail.stock.arch

import com.tngtech.archunit.core.domain.JavaClasses
import com.tngtech.archunit.core.importer.ClassFileImporter
import com.tngtech.archunit.lang.ArchRule
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses
import org.junit.jupiter.api.Test

class ArchitectureTest {

    private val importedClasses: JavaClasses = ClassFileImporter()
        .importPackages("com.andesretail.stock")

    @Test
    fun `domain no depende de application ni infrastructure`() {
        val rule: ArchRule = noClasses()
            .that()
            .resideInAPackage("..domain..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage("..application..", "..infrastructure..", "..web..")

        rule.check(importedClasses)
    }

    @Test
    fun `application no depende de infrastructure`() {
        val rule: ArchRule = noClasses()
            .that()
            .resideInAPackage("..application..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage("..infrastructure..", "..web..")

        rule.check(importedClasses)
    }
}
