package com.shinhan.corebank;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.shinhan.corebank.common.config.JpaAuditingConfig;
import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaMethodCall;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.MonthDay;
import java.time.OffsetDateTime;
import java.time.OffsetTime;
import java.time.Year;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Set;

// 시각은 주입된 Clock(KST 고정, JpaAuditingConfig)으로만 구한다 — 영업일은 BusinessDateProvider (#473)
@AnalyzeClasses(packages = "com.shinhan.corebank", importOptions = ImportOption.DoNotIncludeTests.class)
class TimeSourceArchitectureTest {

    private static final Set<String> TIME_TYPES = Set.of(
            LocalDate.class.getName(),
            LocalDateTime.class.getName(),
            LocalTime.class.getName(),
            Instant.class.getName(),
            ZonedDateTime.class.getName(),
            OffsetDateTime.class.getName(),
            OffsetTime.class.getName(),
            Year.class.getName(),
            YearMonth.class.getName(),
            MonthDay.class.getName());

    // 새 시계를 만드는 Clock static factory — withZone 은 기존 코드의 clock.withZone(KST) 관행이라 허용
    private static final Set<String> CLOCK_FACTORIES = Set.of(
            "system",
            "systemUTC",
            "systemDefaultZone",
            "fixed",
            "offset",
            "tick",
            "tickSeconds",
            "tickMinutes",
            "tickMillis");

    @ArchTest
    static final ArchRule noNowWithoutClock = noClasses()
            .should()
            .callMethodWhere(DescribedPredicate.describe(
                    "인자 없는 now() 또는 now(ZoneId)", TimeSourceArchitectureTest::isNowWithoutClock))
            .because("서버 시계·시간대를 직접 읽으면 KST 계약을 우회한다(4주차 UTC 채번 버그). now(clock)을 쓴다");

    @ArchTest
    static final ArchRule clockIsCreatedOnlyInConfig = noClasses()
            .that()
            .doNotHaveFullyQualifiedName(JpaAuditingConfig.class.getName())
            .should()
            .callMethodWhere(DescribedPredicate.describe(
                    "Clock static factory(system*·fixed·offset·tick*)", TimeSourceArchitectureTest::createsClock))
            .because("Clock 은 KST 고정 빈 하나만 둔다. fixed·offset 등으로 만들면 now(clock) 규칙을 우회한다");

    private static boolean isNowWithoutClock(JavaMethodCall call) {
        if (!call.getName().equals("now")
                || !TIME_TYPES.contains(call.getTargetOwner().getName())) {
            return false;
        }
        List<JavaClass> params = call.getTarget().getRawParameterTypes();
        return params.isEmpty() || params.get(0).isAssignableTo(ZoneId.class);
    }

    private static boolean createsClock(JavaMethodCall call) {
        return call.getTargetOwner().isEquivalentTo(Clock.class) && CLOCK_FACTORIES.contains(call.getName());
    }
}
