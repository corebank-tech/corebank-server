package com.shinhan.corebank.autotransfer.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.shinhan.corebank.autotransfer.domain.AutoTransferErrorCode;
import com.shinhan.corebank.autotransfer.domain.AutoTransferStatus;
import com.shinhan.corebank.common.exception.BusinessException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class AutoTransferJpaEntityTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2025, 6, 1, 0, 0);
    private static final LocalDate START = LocalDate.of(2025, 6, 2);
    private static final LocalDate END = LocalDate.of(2027, 6, 2);
    private static final LocalDate NEXT_EXECUTION = LocalDate.of(2026, 1, 31);

    private AutoTransferJpaEntity register() {
        return AutoTransferJpaEntity.register(
                1L, 1L, "110987654321", "홍길동",
                10000L, 1, 31,
                START, END, NEXT_EXECUTION,
                "내메모", "받는메모", NOW);
    }

    @Nested
    @DisplayName("register()")
    class Register {

        @Test
        @DisplayName("유효한 값이면 NORMAL 상태로 등록된다")
        void success() {
            AutoTransferJpaEntity e = register();

            assertThat(e.getStatus()).isEqualTo(AutoTransferStatus.NORMAL);
            assertThat(e.getRegisteredAt()).isEqualTo(NOW);
            assertThat(e.getNextExecutionDate()).isEqualTo(NEXT_EXECUTION);
        }

        @Test
        @DisplayName("이체지정일이 1~31 범위 밖이면 AUT0001")
        void invalidTransferDay() {
            assertThatThrownBy(() -> AutoTransferJpaEntity.register(
                    1L, 1L, "110987654321", "홍길동",
                    10000L, 1, 32,
                    START, END, NEXT_EXECUTION, null, null, NOW))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                            .isEqualTo(AutoTransferErrorCode.INVALID_TRANSFER_DAY));
        }

        @Test
        @DisplayName("시작일이 당일이면 AUT0002")
        void startDateToday() {
            LocalDate today = NOW.toLocalDate();
            assertThatThrownBy(() -> AutoTransferJpaEntity.register(
                    1L, 1L, "110987654321", "홍길동",
                    10000L, 1, 31,
                    today, END, NEXT_EXECUTION, null, null, NOW))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                            .isEqualTo(AutoTransferErrorCode.INVALID_TRANSFER_PERIOD));
        }

        @Test
        @DisplayName("시작일이 익일부터 1년을 초과하면 AUT0002")
        void startDateTooFar() {
            LocalDate tooFar = NOW.toLocalDate().plusDays(366);
            assertThatThrownBy(() -> AutoTransferJpaEntity.register(
                    1L, 1L, "110987654321", "홍길동",
                    10000L, 1, 31,
                    tooFar, tooFar.plusMonths(1), tooFar, null, null, NOW))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                            .isEqualTo(AutoTransferErrorCode.INVALID_TRANSFER_PERIOD));
        }

        @Test
        @DisplayName("종료일이 시작일보다 빠르면 AUT0002")
        void endDateBeforeStart() {
            assertThatThrownBy(() -> AutoTransferJpaEntity.register(
                    1L, 1L, "110987654321", "홍길동",
                    10000L, 1, 31,
                    START, START.minusDays(1), NEXT_EXECUTION, null, null, NOW))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                            .isEqualTo(AutoTransferErrorCode.INVALID_TRANSFER_PERIOD));
        }

        @Test
        @DisplayName("종료일이 시작일로부터 60개월을 초과하면 AUT0002")
        void endDateTooFar() {
            LocalDate over60Months = START.plusMonths(60).plusDays(1);
            assertThatThrownBy(() -> AutoTransferJpaEntity.register(
                    1L, 1L, "110987654321", "홍길동",
                    10000L, 1, 31,
                    START, over60Months, NEXT_EXECUTION, null, null, NOW))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                            .isEqualTo(AutoTransferErrorCode.INVALID_TRANSFER_PERIOD));
        }

        @Test
        @DisplayName("최초 이체 예정일이 종료일 이후면 AUT0004")
        void noExecutionWithinPeriod() {
            LocalDate shortEnd = START.plusDays(1);
            assertThatThrownBy(() -> AutoTransferJpaEntity.register(
                    1L, 1L, "110987654321", "홍길동",
                    10000L, 1, 31,
                    START, shortEnd, NEXT_EXECUTION, null, null, NOW))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                            .isEqualTo(AutoTransferErrorCode.NO_EXECUTION_WITHIN_PERIOD));
        }

        @Test
        @DisplayName("지원하지 않는 이체주기면 예외")
        void invalidCycle() {
            assertThatThrownBy(() -> AutoTransferJpaEntity.register(
                    1L, 1L, "110987654321", "홍길동",
                    10000L, 2, 31,
                    START, END, NEXT_EXECUTION, null, null, NOW))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("terminate()")
    class Terminate {

        @Test
        @DisplayName("정상 상태이고 실행 예정일 당일이 아니면 해지된다")
        void success() {
            AutoTransferJpaEntity e = register();
            LocalDateTime terminatedNow = NEXT_EXECUTION.minusDays(1).atStartOfDay();

            e.terminate(terminatedNow);

            assertThat(e.getStatus()).isEqualTo(AutoTransferStatus.TERMINATED);
            assertThat(e.getTerminatedAt()).isEqualTo(terminatedNow);
        }

        @Test
        @DisplayName("이미 정상 상태가 아니면 AUT0302")
        void notInNormalStatus() {
            AutoTransferJpaEntity e = register();
            e.terminate(NEXT_EXECUTION.minusDays(1).atStartOfDay());

            assertThatThrownBy(() -> e.terminate(NEXT_EXECUTION.minusDays(1).atStartOfDay()))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                            .isEqualTo(AutoTransferErrorCode.NOT_IN_NORMAL_STATUS));
        }

        @Test
        @DisplayName("실행 예정일 당일에는 AUT0303")
        void cannotTerminateOnExecutionDate() {
            AutoTransferJpaEntity e = register();

            assertThatThrownBy(() -> e.terminate(NEXT_EXECUTION.atStartOfDay()))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                            .isEqualTo(AutoTransferErrorCode.CANNOT_TERMINATE_ON_EXECUTION_DATE));
        }
    }

    @Nested
    @DisplayName("advanceNextExecutionDate()")
    class AdvanceNextExecutionDate {

        @Test
        @DisplayName("말일 보정이 다음 회차로 누적되지 않는다 (1/31 -> 2/28 -> 3/31)")
        void monthEndClampingDoesNotAccumulate() {
            AutoTransferJpaEntity e = register();
            assertThat(e.getNextExecutionDate()).isEqualTo(LocalDate.of(2026, 1, 31));

            e.advanceNextExecutionDate();
            assertThat(e.getNextExecutionDate()).isEqualTo(LocalDate.of(2026, 2, 28));

            e.advanceNextExecutionDate();
            assertThat(e.getNextExecutionDate()).isEqualTo(LocalDate.of(2026, 3, 31));

            e.advanceNextExecutionDate();
            assertThat(e.getNextExecutionDate()).isEqualTo(LocalDate.of(2026, 4, 30));
        }
    }

    @Nested
    @DisplayName("change()")
    class Change {

        @Test
        @DisplayName("금액·종료일·표시내용을 변경한다")
        void changeMutableFields() {
            AutoTransferJpaEntity e = register();
            LocalDate newEndDate = END.plusMonths(1);

            e.change(20000L, 1, newEndDate, "새내메모", "새받는메모");

            assertThat(e.getAmount()).isEqualTo(20000L);
            assertThat(e.getEndDate()).isEqualTo(newEndDate);
            assertThat(e.getMyPassbookMemo()).isEqualTo("새내메모");
            assertThat(e.getRecipientPassbookMemo()).isEqualTo("새받는메모");
            assertThat(e.getNextExecutionDate()).isEqualTo(NEXT_EXECUTION);
        }

        @Test
        @DisplayName("주기를 바꾸면 다음 실행 예정일이 직전 실행 예정일 기준으로 재산출된다")
        void changeCycleRecalculatesNextExecutionDate() {
            AutoTransferJpaEntity e = register(); // cycle=1, nextExecutionDate=2026-01-31, transferDay=31

            e.change(10000L, 3, END, null, null);

            // anchor = 2026-01 - 1개월 = 2025-12, + 3개월 = 2026-03, day = min(31, 31)
            assertThat(e.getNextExecutionDate()).isEqualTo(LocalDate.of(2026, 3, 31));
        }

        @Test
        @DisplayName("정상 상태가 아니면 AUT0302")
        void notInNormalStatus() {
            AutoTransferJpaEntity e = register();
            e.terminate(NEXT_EXECUTION.minusDays(1).atStartOfDay());

            assertThatThrownBy(() -> e.change(10000L, 1, END, null, null))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                            .isEqualTo(AutoTransferErrorCode.NOT_IN_NORMAL_STATUS));
        }

        @Test
        @DisplayName("종료일이 시작일보다 빠르면 AUT0002")
        void endDateBeforeStart() {
            AutoTransferJpaEntity e = register();

            assertThatThrownBy(() -> e.change(10000L, 1, START.minusDays(1), null, null))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                            .isEqualTo(AutoTransferErrorCode.INVALID_TRANSFER_PERIOD));
        }

        @Test
        @DisplayName("변경된 종료일이 다음 실행 예정일보다 이르면 AUT0004")
        void noExecutionWithinPeriodAfterChange() {
            AutoTransferJpaEntity e = register(); // nextExecutionDate = 2026-01-31

            assertThatThrownBy(() -> e.change(10000L, 1, LocalDate.of(2026, 1, 1), null, null))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                            .isEqualTo(AutoTransferErrorCode.NO_EXECUTION_WITHIN_PERIOD));
        }
    }
}
