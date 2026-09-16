package com.shinhan.corebank.batch;

import static com.tngtech.archunit.base.DescribedPredicate.not;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/**
 * batch 모듈은 다른 모듈에 batch.api 만 공개한다. 내부(application/adapter)를 직접 주입하면 모듈 교체가 막힌다.
 * 패키지 패턴을 절대 경로로 쓴다.
 */
@AnalyzeClasses(packages = "com.shinhan.corebank", importOptions = ImportOption.DoNotIncludeTests.class)
class BatchArchitectureTest {

    @ArchTest
    static final ArchRule batchIsExposedOnlyThroughApi = noClasses()
            .that()
            .resideOutsideOfPackage("com.shinhan.corebank.batch..")
            .should()
            .dependOnClassesThat(resideInAPackage("com.shinhan.corebank.batch..")
                    .and(not(resideInAPackage("com.shinhan.corebank.batch.api.."))))
            .because("다른 모듈은 batch.api(BatchExecutionLockPort)로만 배치 인프라를 참조해야 한다");
}
