package com.shinhan.corebank.business.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.shinhan.corebank.IntegrationTestSupport;
import com.shinhan.corebank.business.api.BusinessDateAdvancer;
import com.shinhan.corebank.business.api.BusinessDateProvider;
import com.shinhan.corebank.business.application.port.in.BusinessDateCatchUpUseCase;
import com.shinhan.corebank.business.support.BusinessDateTestFixture;
import com.shinhan.corebank.common.exception.BusinessException;
import com.shinhan.corebank.common.exception.CommonErrorCode;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

// 커밋·동시성이 검증 대상이라 @Transactional 을 걸지 않고 영업일을 테스트마다 되돌린다
class BusinessDateIntegrationTest extends IntegrationTestSupport {

    @Autowired
    BusinessDateProvider businessDateProvider;

    @Autowired
    BusinessDateCatchUpUseCase businessDateCatchUpUseCase;

    @Autowired
    BusinessDateAdvancer businessDateAdvancer;

    @Autowired
    BusinessDateTestFixture businessDateTestFixture;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    Clock clock;

    private LocalDate original;

    @BeforeEach
    void rememberOriginal() {
        original = businessDateTestFixture.currentInDb();
    }

    @AfterEach
    void restoreOriginal() {
        businessDateTestFixture.moveTo(original);
    }

    @Nested
    @DisplayName("영업일 판정")
    class IsBusinessDay {

        @Test
        @DisplayName("시드된 2026 공휴일 22일은 모두 영업일이 아니다")
        void allSeededHolidaysAreNotBusinessDays() {
            List<LocalDate> holidays = jdbcTemplate.queryForList(
                    "SELECT holiday_date FROM holiday WHERE YEAR(holiday_date) = 2026", LocalDate.class);

            assertThat(holidays).hasSize(22);
            assertThat(holidays).allSatisfy(date -> assertThat(businessDateProvider.isBusinessDay(date))
                    .as("%s", date)
                    .isFalse());
        }

        @ParameterizedTest(name = "{0} {1} -> {2}")
        @CsvSource({
            "2026-09-24, 추석 연휴(목), false",
            "2026-09-26, 추석 연휴(토), false",
            "2026-09-28, 대체공휴일 아님(월), true",
            "2026-10-05, 대체공휴일 개천절(월), false",
            "2026-10-09, 한글날(금), false",
            "2026-05-01, 노동절(금), false",
            "2026-07-17, 제헌절(금), false",
            "2026-10-10, 토요일, false",
            "2026-10-11, 일요일, false",
            "2026-10-06, 평일, true",
            "2026-12-31, 평일(은행 영업), true"
        })
        void judgesBusinessDay(LocalDate date, String label, boolean expected) {
            assertThat(businessDateProvider.isBusinessDay(date)).isEqualTo(expected);
        }
    }

    @ParameterizedTest(name = "{0} 다음 영업일 = {1}")
    @CsvSource({
        "2026-10-02, 2026-10-06", // 금 -> 토·일·대체공휴일 건너뜀
        "2026-09-23, 2026-09-28", // 추석 연휴 + 주말
        "2026-10-08, 2026-10-12", // 한글날(금) + 주말
        "2026-10-06, 2026-10-07", // 평일 -> 평일
        "2026-10-03, 2026-10-06" // 휴일에서 시작해도 그 날 자신은 제외
    })
    @DisplayName("다음 영업일은 주말과 공휴일을 건너뛴다")
    void nextBusinessDaySkipsWeekendsAndHolidays(LocalDate date, LocalDate expected) {
        assertThat(businessDateProvider.nextBusinessDay(date)).isEqualTo(expected);
    }

    @Test
    @DisplayName("today()는 DB에 저장된 영업일을 따른다 — 테스트 훅으로 옮기면 그 날짜가 나온다")
    void todayFollowsStoredDate() {
        businessDateTestFixture.moveTo(LocalDate.of(2026, 11, 3));

        assertThat(businessDateProvider.today()).isEqualTo(LocalDate.of(2026, 11, 3));
    }

    @Test
    @DisplayName("영업일 행이 없으면 CMN9001 로 거부한다")
    void todayWithoutRowThrows() {
        jdbcTemplate.update("DELETE FROM business_date WHERE date_type = 'BUSINESS_DATE'");
        try {
            assertThatThrownBy(() -> businessDateProvider.today())
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(CommonErrorCode.BUSINESS_DATE_NOT_FOUND);
        } finally {
            jdbcTemplate.update(
                    "INSERT INTO business_date (date_type, business_date) VALUES ('BUSINESS_DATE', ?)", original);
        }
    }

    @Nested
    @DisplayName("달력에 맞추기")
    class CatchUp {

        @Test
        @DisplayName("Flyway 초기값(2026-01-01)이 아니라 기동 시 오늘 달력에 맞춰져 있다")
        void alignedToCalendarOnStartup() {
            LocalDate calendarToday = LocalDate.now(clock);
            LocalDate expected = businessDateProvider.isBusinessDay(calendarToday)
                    ? calendarToday
                    : businessDateProvider.nextBusinessDay(calendarToday);

            assertThat(original).isEqualTo(expected);
        }

        @Test
        @DisplayName("연휴 동안 매일 불려도 영업일이 앞서 나가지 않는다")
        void doesNotRunAheadDuringHolidays() {
            businessDateTestFixture.moveTo(LocalDate.of(2026, 10, 2));

            businessDateCatchUpUseCase.catchUpTo(LocalDate.of(2026, 10, 3));
            assertThat(businessDateTestFixture.currentInDb()).isEqualTo(LocalDate.of(2026, 10, 6));

            businessDateCatchUpUseCase.catchUpTo(LocalDate.of(2026, 10, 4));
            businessDateCatchUpUseCase.catchUpTo(LocalDate.of(2026, 10, 5));
            businessDateCatchUpUseCase.catchUpTo(LocalDate.of(2026, 10, 6));
            assertThat(businessDateTestFixture.currentInDb()).isEqualTo(LocalDate.of(2026, 10, 6));

            businessDateCatchUpUseCase.catchUpTo(LocalDate.of(2026, 10, 7));
            assertThat(businessDateTestFixture.currentInDb()).isEqualTo(LocalDate.of(2026, 10, 7));
        }

        @Test
        @DisplayName("며칠 뒤처져 있어도 한 번에 따라잡는다 (늦은 배포)")
        void catchesUpInOneCall() {
            businessDateTestFixture.moveTo(LocalDate.of(2026, 10, 2));

            businessDateCatchUpUseCase.catchUpTo(LocalDate.of(2026, 10, 16));

            assertThat(businessDateTestFixture.currentInDb()).isEqualTo(LocalDate.of(2026, 10, 16));
        }

        @Test
        @DisplayName("저장값이 달력보다 앞서 있으면 되돌리지 않는다")
        void neverMovesBackward() {
            businessDateTestFixture.moveTo(LocalDate.of(2026, 10, 2));

            businessDateCatchUpUseCase.catchUpTo(LocalDate.of(2026, 9, 30));

            assertThat(businessDateTestFixture.currentInDb()).isEqualTo(LocalDate.of(2026, 10, 2));
        }

        @Test
        @DisplayName("여러 인스턴스가 동시에 불러도 목표 날짜로 한 번만 맞춰진다")
        void concurrentCallsConverge() throws Exception {
            businessDateTestFixture.moveTo(LocalDate.of(2026, 10, 2));

            List<Object> results = runConcurrently(8, () -> {
                businessDateCatchUpUseCase.catchUpTo(LocalDate.of(2026, 10, 3));
                return "ok";
            });

            assertThat(results).containsOnly("ok");
            assertThat(businessDateTestFixture.currentInDb()).isEqualTo(LocalDate.of(2026, 10, 6));
        }
    }

    @Nested
    @DisplayName("COB 영업일 전환")
    class Advance {

        @Test
        @DisplayName("처리한 영업일에서 다음 영업일로 넘긴다")
        void advancesToNextBusinessDay() {
            businessDateTestFixture.moveTo(LocalDate.of(2026, 10, 2));

            LocalDate next = businessDateAdvancer.advanceFrom(LocalDate.of(2026, 10, 2));

            assertThat(next).isEqualTo(LocalDate.of(2026, 10, 6));
            assertThat(businessDateTestFixture.currentInDb()).isEqualTo(LocalDate.of(2026, 10, 6));
        }

        @Test
        @DisplayName("같은 영업일로 다시 불려도(실패 스텝 재실행) 두 번 넘기지 않는다")
        void retryDoesNotAdvanceTwice() {
            businessDateTestFixture.moveTo(LocalDate.of(2026, 10, 2));

            businessDateAdvancer.advanceFrom(LocalDate.of(2026, 10, 2));
            LocalDate retried = businessDateAdvancer.advanceFrom(LocalDate.of(2026, 10, 2));

            assertThat(retried).isEqualTo(LocalDate.of(2026, 10, 6));
            assertThat(businessDateTestFixture.currentInDb()).isEqualTo(LocalDate.of(2026, 10, 6));
        }

        @Test
        @DisplayName("동시에 두 번 불려도 한 영업일만 넘어간다")
        void concurrentAdvanceMovesOnce() throws Exception {
            businessDateTestFixture.moveTo(LocalDate.of(2026, 10, 2));

            List<Object> results =
                    runConcurrently(8, () -> businessDateAdvancer.advanceFrom(LocalDate.of(2026, 10, 2)));

            assertThat(results).containsOnly(LocalDate.of(2026, 10, 6));
            assertThat(businessDateTestFixture.currentInDb()).isEqualTo(LocalDate.of(2026, 10, 6));
        }

        @Test
        @DisplayName("저장값이 처리한 영업일과 다음 영업일 어느 쪽도 아니면 CMN0303 으로 드러낸다")
        void mismatchThrows() {
            businessDateTestFixture.moveTo(LocalDate.of(2026, 10, 8));

            assertThatThrownBy(() -> businessDateAdvancer.advanceFrom(LocalDate.of(2026, 10, 2)))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(CommonErrorCode.CONCURRENT_MODIFICATION);
            assertThat(businessDateTestFixture.currentInDb()).isEqualTo(LocalDate.of(2026, 10, 8));
        }
    }

    // 예외도 결과로 모아 스레드 안 실패가 묻히지 않게 한다
    private static List<Object> runConcurrently(int threads, Callable<Object> task) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<Object>> futures = new java.util.ArrayList<>();
            for (int i = 0; i < threads; i++) {
                futures.add(pool.submit(() -> {
                    start.await();
                    try {
                        return task.call();
                    } catch (Exception e) {
                        return e;
                    }
                }));
            }
            start.countDown();
            List<Object> results = new java.util.ArrayList<>();
            for (Future<Object> future : futures) {
                results.add(future.get());
            }
            return results;
        } finally {
            pool.shutdownNow();
        }
    }
}
