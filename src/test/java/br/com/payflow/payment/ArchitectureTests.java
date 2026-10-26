package br.com.payflow.payment;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

// Guards the Clean Architecture + Vertical Slice rules described in the README (same rules as the merchant-service).
class ArchitectureTests {

    private static final JavaClasses CLASSES = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("br.com.payflow.payment");

    private static final String[] FRAMEWORKS = {
            "org.springframework..", "jakarta..", "org.hibernate..", "org.flywaydb..", "com.microsoft.sqlserver..",
            "com.azure..", "io.github.resilience4j..", "io.swagger..", "tools.jackson..", "com.fasterxml.jackson.."};

    // Spring wiring (*Slice), HTTP adapters (*Endpoint, with nested request/response records).
    private static final String ADAPTER_NAMES = ".*(Slice|Endpoint)(\\$.*)?";

    private static final DescribedPredicate<JavaClass> ENDPOINTS_OR_WEB = DescribedPredicate.describe(
            "an Endpoint or web infrastructure",
            javaClass -> javaClass.getPackageName().endsWith(".payment.api")
                    || javaClass.getPackageName().endsWith(".payment.infrastructure.web")
                    || javaClass.getName().matches(".*Endpoint(\\$.*)?"));

    @Test
    void featureSlicesDoNotDependOnEachOther() {
        slices().matching("br.com.payflow.payment.features.(*)..")
                .should().notDependOnEachOther()
                .check(CLASSES);
    }

    @Test
    void domainIsFrameworkFreeAndIndependentOfOuterLayers() {
        noClasses().that().resideInAPackage("..payment.domain..")
                .should().dependOnClassesThat().resideInAnyPackage(FRAMEWORKS)
                .orShould().dependOnClassesThat().resideInAnyPackage(
                        "..payment.features..", "..payment.infrastructure..", "..payment.api..")
                .check(CLASSES);
    }

    @Test
    void useCasesAndPortsAreFrameworkFree() {
        noClasses().that().resideInAPackage("..payment.features..")
                .and().haveNameNotMatching(ADAPTER_NAMES)
                .should().dependOnClassesThat().resideInAnyPackage(FRAMEWORKS)
                .check(CLASSES);
    }

    @Test
    void slicesNeverReachInfrastructureDirectly() {
        noClasses().that().resideInAPackage("..payment.features..")
                .should().dependOnClassesThat().resideInAPackage("..payment.infrastructure..")
                .check(CLASSES);
    }

    @Test
    void sharedApiRepresentationIsOnlyUsedByEndpointsAndWebInfrastructure() {
        classes().that().resideInAPackage("..payment.api..")
                .should().onlyBeAccessed().byClassesThat(ENDPOINTS_OR_WEB)
                .check(CLASSES);
    }

    @Test
    void persistenceAndMessagingStayInsideInfrastructure() {
        noClasses().that().resideOutsideOfPackage("..payment.infrastructure..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "jakarta.persistence..", "org.hibernate..", "com.azure..", "io.github.resilience4j..")
                .check(CLASSES);
    }
}
