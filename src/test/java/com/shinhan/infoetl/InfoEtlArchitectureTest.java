package com.shinhan.infoetl;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

// 은행 앱의 "adapter → application → domain" 규칙(AGENTS §3-1)을 정보계 ETL에도 건다.
@AnalyzeClasses(packages = "com.shinhan.infoetl", importOptions = ImportOption.DoNotIncludeTests.class)
class InfoEtlArchitectureTest {

    @ArchTest
    static final ArchRule domainDoesNotDependOnOuterLayersOrJdbc = noClasses()
            .that()
            .resideInAPackage("com.shinhan.infoetl.domain..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage(
                    "com.shinhan.infoetl.application..", "com.shinhan.infoetl.adapter..", "java.sql..", "javax.sql..");

    @ArchTest
    static final ArchRule applicationDoesNotDependOnAdaptersOrJdbc = noClasses()
            .that()
            .resideInAPackage("com.shinhan.infoetl.application..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage("com.shinhan.infoetl.adapter..", "java.sql..", "javax.sql..");
}
