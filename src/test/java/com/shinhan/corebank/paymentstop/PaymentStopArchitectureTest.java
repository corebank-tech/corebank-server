package com.shinhan.corebank.paymentstop;

import static com.tngtech.archunit.base.DescribedPredicate.not;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAnyPackage;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/**
 * paymentstop 은 새 도메인이라 처음부터 계약면으로 잠근다(AGENTS.md 규칙 2, ADR-0006).
 * 패키지 패턴을 절대 경로로 쓴다. 다른 도메인에 adapter.out.paymentstop 같은 패키지가 생겨도 잡히지 않게 한다.
 */
@AnalyzeClasses(packages = "com.shinhan.corebank", importOptions = ImportOption.DoNotIncludeTests.class)
class PaymentStopArchitectureTest {

    private static final String PAYMENT_STOP = "com.shinhan.corebank.paymentstop..";

    @ArchTest
    static final ArchRule otherDomainsUseOnlyPaymentStopApi = noClasses()
            .that()
            .resideOutsideOfPackage(PAYMENT_STOP)
            .should()
            .dependOnClassesThat(
                    resideInAPackage(PAYMENT_STOP).and(not(resideInAPackage("com.shinhan.corebank.paymentstop.api.."))))
            .because("다른 도메인은 paymentstop.api 로만 지급정지 사건을 다룬다");

    @ArchTest
    static final ArchRule paymentStopUsesOnlyOtherDomainApis = noClasses()
            .that()
            .resideInAPackage(PAYMENT_STOP)
            .should()
            .dependOnClassesThat(resideInAPackage("com.shinhan.corebank..")
                    .and(not(resideInAnyPackage(
                            PAYMENT_STOP, "com.shinhan.corebank.common..", "com.shinhan.corebank..api.."))))
            .because("paymentstop 은 다른 도메인의 api 로만 계좌와 이체를 다룬다. 우회 예외를 새로 만들지 않는다");
}
