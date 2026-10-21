package com.shinhan.corebank;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.shinhan.corebank.business.api.BusinessDateProvider;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

// 거래일은 gate 연산의 반환값으로만 읽는다 — today() 를 따로 읽으면 cutoff 보장이 조용히 깨진다 (ADR-0008, #556)
@AnalyzeClasses(packages = "com.shinhan.corebank", importOptions = ImportOption.DoNotIncludeTests.class)
class BusinessDateGateArchitectureTest {

    @ArchTest
    static final ArchRule todayIsOnlyReadByBusinessAndBatch = noClasses()
            .that()
            .resideOutsideOfPackages("..business..", "..batch..")
            .should()
            .callMethod(BusinessDateProvider.class, "today")
            .because("거래일은 BusinessDateGate.acquireSharedAndReadBusinessDate() 로만 읽는다");
}
