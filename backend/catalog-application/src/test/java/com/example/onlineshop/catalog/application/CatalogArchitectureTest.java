package com.example.onlineshop.catalog.application;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.*; import com.tngtech.archunit.core.importer.ClassFileImporter; import org.junit.jupiter.api.Test;
class CatalogArchitectureTest {
 @Test void domainIsPureAndCatalogDoesNotDependOnOrder(){var classes=new ClassFileImporter().importPackages("com.example.onlineshop.catalog");noClasses().that().resideInAPackage("..catalog.domain..").should().dependOnClassesThat().resideInAnyPackage("org.springframework..","jakarta.persistence..","..order..").check(classes);noClasses().that().resideInAnyPackage("..catalog.domain..","..catalog.application..").should().dependOnClassesThat().resideInAnyPackage("..catalog.persistence..","..catalog.api..","..order..").check(classes);}
}
