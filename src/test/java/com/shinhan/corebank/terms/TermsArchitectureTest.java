package com.shinhan.corebank.terms;

import static com.tngtech.archunit.base.DescribedPredicate.not;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/**
 * terms 모듈은 다른 모듈에 terms.api 만 공개한다. 어댑터를 직접 주입하면 모듈 교체가 막힌다.
 * 패키지 패턴을 절대 경로로 쓴다 — 상품 모듈에도 adapter.out.terms 패키지가 있어 "..terms.."는 그쪽까지 잡는다.
 */
@AnalyzeClasses(packages = "com.shinhan.corebank", importOptions = ImportOption.DoNotIncludeTests.class)
class TermsArchitectureTest {

    @ArchTest
    static final ArchRule termsIsExposedOnlyThroughApi = noClasses()
            .that()
            .resideOutsideOfPackage("com.shinhan.corebank.terms..")
            .should()
            .dependOnClassesThat(resideInAPackage("com.shinhan.corebank.terms..")
                    .and(not(resideInAPackage("com.shinhan.corebank.terms.api.."))))
            .because("다른 모듈은 terms.api(TermsQueryPort)로만 약관을 조회해야 한다");
}
