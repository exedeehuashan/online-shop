package com.example.onlineshop.order.application;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.*;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;

class ArchitectureTest {
  @Test void domainIsPureAndApplicationDependsOnlyInward() {
    var classes = new ClassFileImporter().withImportOption(new ImportOption.DoNotIncludeTests())
        .importPackages("com.example.onlineshop.order");
    noClasses().that().resideInAPackage("..domain..").should().dependOnClassesThat()
        .resideOutsideOfPackages("java..", "com.example.onlineshop.order.domain..").check(classes);
    noClasses().that().resideInAPackage("..domain..").should().dependOnClassesThat()
        .resideInAnyPackage("org.springframework..", "jakarta.persistence..", "org.apache.kafka..").check(classes);
    noClasses().that().resideInAPackage("..application..").should().dependOnClassesThat()
        .resideOutsideOfPackages("java..", "com.example.onlineshop.order.application..",
            "com.example.onlineshop.order.domain..").check(classes);
  }
}
