package br.com.oficina.execucao.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Regra de dependência da Clean Architecture: o núcleo (entities, usecases,
 * gateways) não conhece Spring, AWS nem Jackson.
 */
class ArchitectureTest {

    private static final String[] FRAMEWORKS = {
            "org.springframework..", "software.amazon..", "io.awspring..",
            "com.fasterxml.jackson.."};

    private static JavaClasses classes;

    @BeforeAll
    static void importaClasses() {
        classes = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("br.com.oficina.execucao");
    }

    @Test
    void entitiesNaoDependemDeFramework() {
        noClasses().that().resideInAPackage("..entities..")
                .should().dependOnClassesThat().resideInAnyPackage(FRAMEWORKS)
                .allowEmptyShould(true)
                .check(classes);
    }

    @Test
    void usecasesNaoDependemDeFramework() {
        noClasses().that().resideInAPackage("..usecases..")
                .should().dependOnClassesThat().resideInAnyPackage(FRAMEWORKS)
                .allowEmptyShould(true)
                .check(classes);
    }

    @Test
    void gatewaysNaoDependemDeFramework() {
        noClasses().that().resideInAPackage("..gateways..")
                .should().dependOnClassesThat().resideInAnyPackage(FRAMEWORKS)
                .allowEmptyShould(true)
                .check(classes);
    }

    @Test
    void entitiesNaoDependemDeCamadasExternas() {
        noClasses().that().resideInAPackage("..entities..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "..usecases..", "..gateways..", "..controllers..",
                        "..presenters..", "..infrastructure..")
                .allowEmptyShould(true)
                .check(classes);
    }

    @Test
    void usecasesNaoDependemDeAdapters() {
        noClasses().that().resideInAPackage("..usecases..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "..infrastructure..", "..controllers..", "..presenters..")
                .allowEmptyShould(true)
                .check(classes);
    }
}
