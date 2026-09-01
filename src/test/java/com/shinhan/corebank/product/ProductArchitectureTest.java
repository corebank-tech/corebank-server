package com.shinhan.corebank.product;

import static com.tngtech.archunit.library.Architectures.layeredArchitecture;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/**
 * "의존 방향 adapter → application → domain" 규칙을 자동 검증한다.
 * product 도메인 범위로만 스캔한다(다른 도메인이 같은 구조를 갖추면 그때 확장)
 */
@AnalyzeClasses(packages = "com.shinhan.corebank.product", importOptions = ImportOption.DoNotIncludeTests.class)
class ProductArchitectureTest {

    @ArchTest
    static final ArchRule layerDependenciesAreRespected = layeredArchitecture()
            .consideringOnlyDependenciesInAnyPackage("..product..")
            .layer("Domain")
            .definedBy("..product.domain..")
            .layer("Application")
            .definedBy("..product.application..")
            .layer("Adapter")
            .definedBy("..product.adapter..")
            .whereLayer("Domain")
            .mayNotAccessAnyLayer()
            .whereLayer("Application")
            .mayOnlyAccessLayers("Domain")
            .whereLayer("Application")
            .mayOnlyBeAccessedByLayers("Adapter")
            .whereLayer("Adapter")
            .mayOnlyAccessLayers("Application", "Domain");
}
