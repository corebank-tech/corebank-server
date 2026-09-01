package com.shinhan.corebank.autotransfer.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.shinhan.corebank.common.exception.BusinessException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class AutoTransferTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2025, 6, 1, 0, 0);
    private static final LocalDate START = LocalDate.of(2025, 6, 2);
    private static final LocalDate END = LocalDate.of(2027, 6, 2);
    private static final int TRANSFER_DAY = 15;
    // START(2025-06-02) 이후 첫 15일 = 2025-06-15
    private static final LocalDate NEXT_EXECUTION = LocalDate.of(2025, 6, 15);

    private AutoTransfer register() {
        return AutoTransfer.register(
                1L, 1L, "110987654321", "홍길동", 10000L, 1, TRANSFER_DAY, START, END, "내메모", "받는메모", NOW);
    }

    @Nested
    @DisplayName("register()")
    class Register {

        @Test
        @DisplayName("유효한 값이면 NORMAL 상태로 등록되고 최초 실행 예정일이 startDate 이후 첫 transferDay로 계산된다")
        void success() {
            AutoTransfer e = register();

            assertThat(e.getStatus()).isEqualTo(AutoTransferStatus.NORMAL);
            assertThat(e.getRegisteredAt()).isEqualTo(NOW);
            assertThat(e.getNextExecutionDate()).isEqualTo(NEXT_EXECUTION);
        }

        @Test
        @DisplayName("transferDay가 startDate의 일자보다 이미 지났으면 다음 달로 넘어간다")
        void firstExecutionDateRollsToNextMonthWhenTransferDayAlreadyPassed() {
            // 시작일 2026-08-06, 이체지정일 5일 -> 8월 5일은 이미 지났으므로 9월 5일이어야 한다
            LocalDateTime now = LocalDateTime.of(2026, 8, 5, 0, 0);
            LocalDate startDate = LocalDate.of(2026, 8, 6);

            AutoTransfer e = AutoTransfer.register(
                    1L, 1L, "110987654321", "홍길동", 10000L, 1, 5, startDate, startDate.plusMonths(6), null, null, now);

            assertThat(e.getNextExecutionDate()).isEqualTo(LocalDate.of(2026, 9, 5));
        }

        @Test
        @DisplayName("다음 달로 넘어갈 때도 월말 보정이 적용된다")
        void firstExecutionDateAppliesMonthEndClampingAfterRollingToNextMonth() {
            // 시작일 2026-01-31, 이체지정일 30일 -> 1월 30일은 이미 지났으므로 2월로 넘어가고,
            // 2월은 30일이 없으므로(2026년은 평년) 말일인 28일로 보정된다
            LocalDateTime now = LocalDateTime.of(2026, 1, 30, 0, 0);
            LocalDate startDate = LocalDate.of(2026, 1, 31);

            AutoTransfer e = AutoTransfer.register(
                    1L, 1L, "110987654321", "홍길동", 10000L, 1, 30, startDate, startDate.plusMonths(6), null, null, now);

            assertThat(e.getNextExecutionDate()).isEqualTo(LocalDate.of(2026, 2, 28));
        }

        @Test
        @DisplayName("transferDay가 startDate의 일자와 같으면 최초 실행일은 startDate 그대로다")
        void firstExecutionDateEqualsStartDateWhenTransferDayMatches() {
            LocalDateTime now = LocalDateTime.of(2025, 6, 1, 0, 0);
            LocalDate startDate = LocalDate.of(2025, 6, 15);

            AutoTransfer e = AutoTransfer.register(
                    1L, 1L, "110987654321", "홍길동", 10000L, 1, 15, startDate, startDate.plusMonths(6), null, null, now);

            assertThat(e.getNextExecutionDate()).isEqualTo(startDate);
        }

        @Test
        @DisplayName("같은 달 안에서도 말일 보정이 적용된다 (평년 2월)")
        void firstExecutionDateAppliesMonthEndClampingWithinSameMonth() {
            LocalDateTime now = LocalDateTime.of(2026, 1, 1, 0, 0);
            LocalDate startDate = LocalDate.of(2026, 2, 1);

            AutoTransfer e = AutoTransfer.register(
                    1L, 1L, "110987654321", "홍길동", 10000L, 1, 31, startDate, startDate.plusMonths(6), null, null, now);

            assertThat(e.getNextExecutionDate()).isEqualTo(LocalDate.of(2026, 2, 28));
        }

        @Test
        @DisplayName("윤년 2월은 29일까지 인정한다")
        void firstExecutionDateHandlesLeapYearFebruary() {
            LocalDateTime now = LocalDateTime.of(2028, 1, 1, 0, 0);
            LocalDate startDate = LocalDate.of(2028, 2, 1);

            AutoTransfer e = AutoTransfer.register(
                    1L, 1L, "110987654321", "홍길동", 10000L, 1, 29, startDate, startDate.plusMonths(6), null, null, now);

            assertThat(e.getNextExecutionDate()).isEqualTo(LocalDate.of(2028, 2, 29));
        }

        @Test
        @DisplayName("이체지정일이 1~31 범위 밖이면 AUT0001")
        void invalidTransferDay() {
            assertThatThrownBy(() -> AutoTransfer.register(
                            1L, 1L, "110987654321", "홍길동", 10000L, 1, 32, START, END, null, null, NOW))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                            .isEqualTo(AutoTransferErrorCode.INVALID_TRANSFER_DAY));
        }

        @Test
        @DisplayName("시작일이 당일이면 AUT0002")
        void startDateToday() {
            LocalDate today = NOW.toLocalDate();
            assertThatThrownBy(() -> AutoTransfer.register(
                            1L, 1L, "110987654321", "홍길동", 10000L, 1, TRANSFER_DAY, today, END, null, null, NOW))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                            .isEqualTo(AutoTransferErrorCode.INVALID_TRANSFER_PERIOD));
        }

        @Test
        @DisplayName("시작일이 익일부터 1년을 초과하면 AUT0002")
        void startDateTooFar() {
            LocalDate tooFar = NOW.toLocalDate().plusDays(366);
            assertThatThrownBy(() -> AutoTransfer.register(
                            1L,
                            1L,
                            "110987654321",
                            "홍길동",
                            10000L,
                            1,
                            TRANSFER_DAY,
                            tooFar,
                            tooFar.plusMonths(1),
                            null,
                            null,
                            NOW))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                            .isEqualTo(AutoTransferErrorCode.INVALID_TRANSFER_PERIOD));
        }

        @Test
        @DisplayName("종료일이 시작일보다 빠르면 AUT0002")
        void endDateBeforeStart() {
            assertThatThrownBy(() -> AutoTransfer.register(
                            1L,
                            1L,
                            "110987654321",
                            "홍길동",
                            10000L,
                            1,
                            TRANSFER_DAY,
                            START,
                            START.minusDays(1),
                            null,
                            null,
                            NOW))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                            .isEqualTo(AutoTransferErrorCode.INVALID_TRANSFER_PERIOD));
        }

        @Test
        @DisplayName("종료일이 시작일로부터 60개월을 초과하면 AUT0002")
        void endDateTooFar() {
            LocalDate over60Months = START.plusMonths(60).plusDays(1);
            assertThatThrownBy(() -> AutoTransfer.register(
                            1L,
                            1L,
                            "110987654321",
                            "홍길동",
                            10000L,
                            1,
                            TRANSFER_DAY,
                            START,
                            over60Months,
                            null,
                            null,
                            NOW))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                            .isEqualTo(AutoTransferErrorCode.INVALID_TRANSFER_PERIOD));
        }

        @Test
        @DisplayName("최초 이체 예정일이 종료일 이후면 AUT0004")
        void noExecutionWithinPeriod() {
            // START(6/2) 이후 첫 transferDay(15일)는 6/15 인데, 종료일을 그보다 앞당겨서 실행 불가능하게 만든다
            LocalDate shortEnd = START.plusDays(1);
            assertThatThrownBy(() -> AutoTransfer.register(
                            1L, 1L, "110987654321", "홍길동", 10000L, 1, TRANSFER_DAY, START, shortEnd, null, null, NOW))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                            .isEqualTo(AutoTransferErrorCode.NO_EXECUTION_WITHIN_PERIOD));
        }

        @Test
        @DisplayName("지원하지 않는 이체주기면 AUT0007")
        void invalidCycle() {
            assertThatThrownBy(() -> AutoTransfer.register(
                            1L, 1L, "110987654321", "홍길동", 10000L, 2, TRANSFER_DAY, START, END, null, null, NOW))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                            .isEqualTo(AutoTransferErrorCode.INVALID_CYCLE_MONTHS));
        }

        @Test
        @DisplayName("금액이 0 이하면 AUT0008 (Command 우회 방어)")
        void invalidAmount() {
            assertThatThrownBy(() -> AutoTransfer.register(
                            1L, 1L, "110987654321", "홍길동", 0L, 1, TRANSFER_DAY, START, END, null, null, NOW))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                            .isEqualTo(AutoTransferErrorCode.INVALID_AMOUNT));
        }
    }

    @Nested
    @DisplayName("terminate()")
    class Terminate {

        @Test
        @DisplayName("정상 상태이고 실행 예정일 당일이 아니면 해지된다")
        void success() {
            AutoTransfer e = register();
            LocalDateTime terminatedNow = NEXT_EXECUTION.minusDays(1).atStartOfDay();

            e.terminate(terminatedNow);

            assertThat(e.getStatus()).isEqualTo(AutoTransferStatus.TERMINATED);
            assertThat(e.getTerminatedAt()).isEqualTo(terminatedNow);
        }

        @Test
        @DisplayName("이미 정상 상태가 아니면 AUT0302")
        void notInNormalStatus() {
            AutoTransfer e = register();
            e.terminate(NEXT_EXECUTION.minusDays(1).atStartOfDay());

            assertThatThrownBy(() -> e.terminate(NEXT_EXECUTION.minusDays(1).atStartOfDay()))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                            .isEqualTo(AutoTransferErrorCode.NOT_IN_NORMAL_STATUS));
        }

        @Test
        @DisplayName("실행 예정일 당일에는 AUT0303")
        void cannotTerminateOnExecutionDate() {
            AutoTransfer e = register();

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
            LocalDateTime now = LocalDateTime.of(2026, 1, 1, 0, 0);
            LocalDate startDate = LocalDate.of(2026, 1, 2);
            AutoTransfer e = AutoTransfer.register(
                    1L, 1L, "110987654321", "홍길동", 10000L, 1, 31, startDate, startDate.plusMonths(6), null, null, now);
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
            AutoTransfer e = register();
            LocalDate newEndDate = END.plusMonths(1);

            e.change(20000L, 1, newEndDate, "새내메모", "새받는메모");

            assertThat(e.getAmount()).isEqualTo(20000L);
            assertThat(e.getEndDate()).isEqualTo(newEndDate);
            assertThat(e.getMyPassbookMemo()).isEqualTo("새내메모");
            assertThat(e.getRecipientPassbookMemo()).isEqualTo("새받는메모");
            assertThat(e.getNextExecutionDate()).isEqualTo(NEXT_EXECUTION);
        }

        @Test
        @DisplayName("모든 필드를 null로 넘기면 기존 값이 그대로 유지된다")
        void allNullFields_keepsExistingValues() {
            AutoTransfer e = register();

            e.change(null, null, null, null, null);

            assertThat(e.getAmount()).isEqualTo(10000L);
            assertThat(e.getCycleMonths()).isEqualTo(1);
            assertThat(e.getEndDate()).isEqualTo(END);
            assertThat(e.getNextExecutionDate()).isEqualTo(NEXT_EXECUTION);
            assertThat(e.getMyPassbookMemo()).isEqualTo("내메모");
            assertThat(e.getRecipientPassbookMemo()).isEqualTo("받는메모");
        }

        @Test
        @DisplayName("일부 필드만 넘기면 null인 필드는 기존 값을 유지하고 나머지만 반영된다")
        void partialFields_onlyProvidedFieldsChange() {
            AutoTransfer e = register();

            e.change(20000L, null, null, null, null);

            assertThat(e.getAmount()).isEqualTo(20000L);
            assertThat(e.getCycleMonths()).isEqualTo(1);
            assertThat(e.getEndDate()).isEqualTo(END);
            assertThat(e.getMyPassbookMemo()).isEqualTo("내메모");
            assertThat(e.getRecipientPassbookMemo()).isEqualTo("받는메모");
        }

        @Test
        @DisplayName("주기를 바꾸면 다음 실행 예정일이 직전 실행 예정일 기준으로 재산출된다")
        void changeCycleRecalculatesNextExecutionDate() {
            AutoTransfer e = register(); // cycle=1, nextExecutionDate=2025-06-15, transferDay=15

            e.change(10000L, 3, END, null, null);

            // anchor = 2025-06 - 1개월 = 2025-05, + 3개월 = 2025-08, day = min(15, 31)
            assertThat(e.getNextExecutionDate()).isEqualTo(LocalDate.of(2025, 8, 15));
        }

        @Test
        @DisplayName("정상 상태가 아니면 AUT0302")
        void notInNormalStatus() {
            AutoTransfer e = register();
            e.terminate(NEXT_EXECUTION.minusDays(1).atStartOfDay());

            assertThatThrownBy(() -> e.change(10000L, 1, END, null, null))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                            .isEqualTo(AutoTransferErrorCode.NOT_IN_NORMAL_STATUS));
        }

        @Test
        @DisplayName("종료일이 시작일보다 빠르면 AUT0002")
        void endDateBeforeStart() {
            AutoTransfer e = register();

            assertThatThrownBy(() -> e.change(10000L, 1, START.minusDays(1), null, null))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                            .isEqualTo(AutoTransferErrorCode.INVALID_TRANSFER_PERIOD));
        }

        @Test
        @DisplayName("금액이 0 이하면 AUT0008 (Command 우회 방어)")
        void invalidAmount() {
            AutoTransfer e = register();

            assertThatThrownBy(() -> e.change(0L, null, null, null, null))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                            .isEqualTo(AutoTransferErrorCode.INVALID_AMOUNT));
        }

        @Test
        @DisplayName("변경된 종료일이 다음 실행 예정일보다 이르면 AUT0004")
        void noExecutionWithinPeriodAfterChange() {
            AutoTransfer e = register(); // nextExecutionDate = 2025-06-15

            assertThatThrownBy(() -> e.change(10000L, 1, LocalDate.of(2025, 6, 3), null, null))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                            .isEqualTo(AutoTransferErrorCode.NO_EXECUTION_WITHIN_PERIOD));
        }

        @Test
        @DisplayName("주기 변경으로 인해 AUT0004가 나면 cycleMonths·nextExecutionDate를 포함해 기존 필드가 그대로 유지된다")
        void failureLeavesEntityUnchanged() {
            AutoTransfer e = register(); // cycle=1, nextExecutionDate=2025-06-15, amount=10000, endDate=END

            // cycle 1 -> 3 이면 newNextExecutionDate = 2025-08-15 인데, endDate를 그보다 이르게 줘서 AUT0004를 유발한다
            assertThatThrownBy(() -> e.change(99999L, 3, LocalDate.of(2025, 7, 1), "새내메모", "새받는메모"))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                            .isEqualTo(AutoTransferErrorCode.NO_EXECUTION_WITHIN_PERIOD));

            assertThat(e.getCycleMonths()).isEqualTo(1);
            assertThat(e.getNextExecutionDate()).isEqualTo(NEXT_EXECUTION);
            assertThat(e.getAmount()).isEqualTo(10000L);
            assertThat(e.getEndDate()).isEqualTo(END);
            assertThat(e.getMyPassbookMemo()).isEqualTo("내메모");
            assertThat(e.getRecipientPassbookMemo()).isEqualTo("받는메모");
        }
    }

    @Nested
    @DisplayName("getExecutions()")
    class Executions {

        @Test
        @DisplayName("addExecution()으로 추가한 회차가 조회된다")
        void addedExecutionIsVisible() {
            AutoTransfer e = register();
            AutoTransferExecution execution =
                    AutoTransferExecution.processing(NEXT_EXECUTION, 10000L, NEXT_EXECUTION.atStartOfDay());

            e.addExecution(execution);

            assertThat(e.getExecutions()).containsExactly(execution);
        }

        @Test
        @DisplayName("불변 뷰라 외부에서 remove()/clear()로 지울 수 없다")
        void executionsViewIsUnmodifiable() {
            AutoTransfer e = register();
            e.addExecution(AutoTransferExecution.processing(NEXT_EXECUTION, 10000L, NEXT_EXECUTION.atStartOfDay()));

            assertThatThrownBy(() -> e.getExecutions().clear()).isInstanceOf(UnsupportedOperationException.class);
            assertThat(e.getExecutions()).hasSize(1);
        }
    }
}
