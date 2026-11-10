package br.com.oficina.execucao.architecture;

import br.com.oficina.execucao.fixtures.camadas.entities.EntidadeQueUsaUseCase;
import br.com.oficina.execucao.fixtures.camadas.infrastructure.AdapterAlvo;
import br.com.oficina.execucao.fixtures.camadas.usecases.UseCaseAlvo;
import br.com.oficina.execucao.fixtures.camadas.usecases.UseCaseQueUsaInfra;
import br.com.oficina.execucao.fixtures.entities.EntidadeComSpring;
import br.com.oficina.execucao.fixtures.gateways.GatewayComAws;
import br.com.oficina.execucao.fixtures.usecases.UseCaseComJackson;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regra de dependência da Clean Architecture: o núcleo (entities, usecases,
 * gateways) não conhece Spring, AWS nem Jackson.
 *
 * <p>Os pacotes do núcleo ainda estão vazios, então as regras usam
 * {@code allowEmptyShould(true)}. Para provar que cada regra pode falhar, os testes
 * "negativos" avaliam as mesmas regras contra classes de fixture que as violam
 * (ver pacote {@code fixtures}, só em test).
 */
class ArchitectureTest {

    private static final String[] FRAMEWORKS = {
            "org.springframework..", "software.amazon..", "io.awspring..",
            "com.fasterxml.jackson.."};

    private static final ArchRule ENTITIES_SEM_FRAMEWORK = noClasses()
            .that().resideInAPackage("..entities..")
            .should().dependOnClassesThat().resideInAnyPackage(FRAMEWORKS)
            .allowEmptyShould(true);

    private static final ArchRule USECASES_SEM_FRAMEWORK = noClasses()
            .that().resideInAPackage("..usecases..")
            .should().dependOnClassesThat().resideInAnyPackage(FRAMEWORKS)
            .allowEmptyShould(true);

    private static final ArchRule GATEWAYS_SEM_FRAMEWORK = noClasses()
            .that().resideInAPackage("..gateways..")
            .should().dependOnClassesThat().resideInAnyPackage(FRAMEWORKS)
            .allowEmptyShould(true);

    private static final ArchRule ENTITIES_SEM_CAMADAS_EXTERNAS = noClasses()
            .that().resideInAPackage("..entities..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "..usecases..", "..gateways..", "..controllers..",
                    "..presenters..", "..infrastructure..")
            .allowEmptyShould(true);

    private static final ArchRule USECASES_SEM_ADAPTERS = noClasses()
            .that().resideInAPackage("..usecases..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "..infrastructure..", "..controllers..", "..presenters..")
            .allowEmptyShould(true);

    private static JavaClasses classes;

    @BeforeAll
    static void importaClasses() {
        classes = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("br.com.oficina.execucao");
    }

    /** Importa fixtures por nome de classe, o que ignora o filtro DO_NOT_INCLUDE_TESTS. */
    private static JavaClasses fixtures(Class<?>... fixtures) {
        return new ClassFileImporter().importClasses(fixtures);
    }

    @Test
    void entitiesNaoDependemDeFramework() {
        ENTITIES_SEM_FRAMEWORK.check(classes);
    }

    @Test
    void usecasesNaoDependemDeFramework() {
        USECASES_SEM_FRAMEWORK.check(classes);
    }

    @Test
    void gatewaysNaoDependemDeFramework() {
        GATEWAYS_SEM_FRAMEWORK.check(classes);
    }

    @Test
    void entitiesNaoDependemDeCamadasExternas() {
        ENTITIES_SEM_CAMADAS_EXTERNAS.check(classes);
    }

    @Test
    void usecasesNaoDependemDeAdapters() {
        USECASES_SEM_ADAPTERS.check(classes);
    }

    @Test
    void regraDeEntitiesSemFrameworkDetectaViolacao() {
        assertThat(ENTITIES_SEM_FRAMEWORK.evaluate(fixtures(EntidadeComSpring.class)).hasViolation())
                .isTrue();
    }

    @Test
    void regraDeUsecasesSemFrameworkDetectaViolacao() {
        assertThat(USECASES_SEM_FRAMEWORK.evaluate(fixtures(UseCaseComJackson.class)).hasViolation())
                .isTrue();
    }

    @Test
    void regraDeGatewaysSemFrameworkDetectaViolacao() {
        assertThat(GATEWAYS_SEM_FRAMEWORK.evaluate(fixtures(GatewayComAws.class)).hasViolation())
                .isTrue();
    }

    @Test
    void regraDeEntitiesSemCamadasExternasDetectaViolacao() {
        var violacao = fixtures(EntidadeQueUsaUseCase.class, UseCaseAlvo.class);

        assertThat(ENTITIES_SEM_CAMADAS_EXTERNAS.evaluate(violacao).hasViolation()).isTrue();
    }

    @Test
    void regraDeUsecasesSemAdaptersDetectaViolacao() {
        var violacao = fixtures(UseCaseQueUsaInfra.class, AdapterAlvo.class);

        assertThat(USECASES_SEM_ADAPTERS.evaluate(violacao).hasViolation()).isTrue();
    }

    @Test
    void fixturesNaoEntramNaImportacaoPrincipal() {
        assertThat(classes.contain(EntidadeComSpring.class)).isFalse();
    }
}
