package com.example.onlineshop.identity.application;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ClassFileImporter;
import org.junit.jupiter.api.Test;

class IdentityArchitectureTest {
  @Test
  void domainAndApplicationRemainIsolatedFromFrameworksAndOtherContexts() {
    var classes = new ClassFileImporter().importPackages("com.example.onlineshop.identity");
    noClasses().that().resideInAPackage("..identity.domain..")
        .should().dependOnClassesThat().resideInAnyPackage("org.springframework..", "jakarta.persistence..")
        .check(classes);
    noClasses().that().resideInAnyPackage("..identity.domain..", "..identity.application..")
        .should().dependOnClassesThat().resideInAnyPackage(
            "..identity.persistence..", "..identity.api..", "..catalog..", "..order..")
        .check(classes);
  }
}
