package com.shinhan.corebank.subscription;

import static com.tngtech.archunit.library.Architectures.layeredArchitecture;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/**
 * "의존 방향 adapter → application → domain" 규칙을 자동 검증한다.
 * subscription 도메인 범위로만 스캔한다. application 계층은 가입 서비스 이슈에서
 * 추가될 예정이라 withOptionalLayers로 비어 있어도 통과하게 둔다.
 */
@AnalyzeClasses(packages = "com.shinhan.corebank.subscription", importOptions = ImportOption.DoNotIncludeTests.class)
class SubscriptionArchitectureTest {

    @ArchTest
    static final ArchRule layerDependenciesAreRespected = layeredArchitecture()
            .consideringOnlyDependenciesInAnyPackage("..subscription..")
            .withOptionalLayers(true)
            .layer("Domain")
            .definedBy("..subscription.domain..")
            .layer("Application")
            .definedBy("..subscription.application..")
            .layer("Adapter")
            .definedBy("..subscription.adapter..")
            .whereLayer("Domain")
            .mayNotAccessAnyLayer()
            .whereLayer("Application")
            .mayOnlyAccessLayers("Domain")
            .whereLayer("Application")
            .mayOnlyBeAccessedByLayers("Adapter")
            .whereLayer("Adapter")
            .mayOnlyAccessLayers("Application", "Domain");
}
