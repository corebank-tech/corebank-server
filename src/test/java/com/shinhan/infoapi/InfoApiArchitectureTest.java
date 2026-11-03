package com.shinhan.infoapi;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

// 은행 앱의 "adapter → application → domain" 규칙(AGENTS §3-1)을 정보계 조회 API에도 건다.
@AnalyzeClasses(packages = "com.shinhan.infoapi", importOptions = ImportOption.DoNotIncludeTests.class)
class InfoApiArchitectureTest {

    @ArchTest
    static final ArchRule domainDoesNotDependOnOuterLayersOrJdbc = noClasses()
            .that()
            .resideInAPackage("com.shinhan.infoapi.domain..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage(
                    "com.shinhan.infoapi.application..", "com.shinhan.infoapi.adapter..", "java.sql..", "javax.sql..");

    @ArchTest
    static final ArchRule applicationDoesNotDependOnAdaptersOrJdbc = noClasses()
            .that()
            .resideInAPackage("com.shinhan.infoapi.application..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage("com.shinhan.infoapi.adapter..", "java.sql..", "javax.sql..");

    @ArchTest
    static final ArchRule inboundAdapterDoesNotKnowOutboundAdapters = noClasses()
            .that()
            .resideInAPackage("com.shinhan.infoapi.adapter.in..")
            .should()
            .dependOnClassesThat()
            .resideInAPackage("com.shinhan.infoapi.adapter.out..");

    // 정보계 API는 마트를 읽기만 한다. 본체 코드를 끌어오지 않고 Spring·JPA도 쓰지 않는다.
    @ArchTest
    static final ArchRule doesNotUseCorebankOrSpring = noClasses()
            .that()
            .resideInAPackage("com.shinhan.infoapi..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage("com.shinhan.corebank..", "org.springframework..", "jakarta.persistence..");

    @ArchTest
    static final ArchRule outboundAdapterOnlyKnowsPortsAndDomain = classes()
            .that()
            .resideInAPackage("com.shinhan.infoapi.adapter.out..")
            .should()
            .onlyDependOnClassesThat()
            .resideInAnyPackage(
                    "com.shinhan.infoapi.adapter.out..",
                    "com.shinhan.infoapi.application.port.out..",
                    "com.shinhan.infoapi.domain..",
                    "java.lang..",
                    "java.sql..",
                    "java.time..",
                    "java.util..");
}
