package com.pelypets.todolist.server;

import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.Architectures.onionArchitecture;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/** Keeps the hexagonal layering from eroding: dependencies point inwards, adapters never call each other. */
@AnalyzeClasses(packages = "com.pelypets.todolist.server", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    @ArchTest
    static final ArchRule hexagonalLayers = onionArchitecture()
            .domainModels("..domain..")
            .domainServices("..application.port..")
            .applicationServices("..application.service..")
            .adapter("web", "..adapter.in.web..")
            .adapter("scheduling", "..adapter.in.scheduling..")
            .adapter("persistence", "..adapter.out.persistence..")
            .adapter("security", "..adapter.out.security..")
            .adapter("metrics", "..adapter.out.metrics..")
            // The composition root wires adapters together; it is the one place allowed to know them all.
            .ignoreDependency(resideInAPackage("..config.."), DescribedPredicate.alwaysTrue());

    @ArchTest
    static final ArchRule domainIsFrameworkFree = noClasses()
            .that().resideInAPackage("..domain..")
            .should().dependOnClassesThat()
            .resideInAnyPackage("org.springframework..", "jakarta..", "org.hibernate..", "tools.jackson..", "io.swagger..");

    @ArchTest
    static final ArchRule inboundAdaptersUseOnlyInboundPorts = noClasses()
            .that().resideInAPackage("..adapter.in..")
            .should().dependOnClassesThat()
            .resideInAnyPackage("..application.service..", "..application.port.out..");

    @ArchTest
    static final ArchRule nothingDependsOnConfiguration = noClasses()
            .that().resideOutsideOfPackage("..config..")
            .and().doNotHaveSimpleName("ToDoServerApplication")
            .should().dependOnClassesThat().resideInAPackage("..config..");
}
