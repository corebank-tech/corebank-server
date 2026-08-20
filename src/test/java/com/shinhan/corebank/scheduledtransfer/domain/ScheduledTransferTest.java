package com.shinhan.corebank.scheduledtransfer.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.shinhan.corebank.common.exception.BusinessException;
import com.shinhan.corebank.scheduledtransfer.domain.exception.ScheduledTransferErrorCode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class ScheduledTransferTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2025, 6, 1, 0, 0);
    private static final String PAYEE_BANK_CODE = "088";

    private ScheduledTransfer register(LocalDate scheduledDate) {
        return ScheduledTransfer.register(
                1L, 2L, PAYEE_BANK_CODE, "110987654321", "홍길동",
                10_000L, scheduledDate, "내메모", "받는메모", NOW);
    }

    @Nested
    @DisplayName("register()")
    class Register {

        @Test
        @DisplayName("유효한 값이면 WAITING 상태로 등록된다")
        void success() {
            ScheduledTransfer s = register(LocalDate.of(2025, 6, 2));

            assertThat(s.getStatus()).isEqualTo(ScheduledTransferStatus.WAITING);
            assertThat(s.getRegisteredAt()).isEqualTo(NOW);
            assertThat(s.getPayeeBankCode()).isEqualTo(PAYEE_BANK_CODE);
        }

        @Test
        @DisplayName("예약일자가 당일이면 SCD0001")
        void scheduledDateToday_throwsInvalidScheduledDate() {
            assertThatThrownBy(() -> register(LocalDate.of(2025, 6, 1)))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                            .isEqualTo(ScheduledTransferErrorCode.INVALID_SCHEDULED_DATE));
        }

        @Test
        @DisplayName("예약일자가 과거면 SCD0001")
        void scheduledDatePast_throwsInvalidScheduledDate() {
            assertThatThrownBy(() -> register(LocalDate.of(2025, 5, 31)))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                            .isEqualTo(ScheduledTransferErrorCode.INVALID_SCHEDULED_DATE));
        }

        @Test
        @DisplayName("예약일자가 익일부터 365일 이내면 등록된다 (경계값)")
        void scheduledDateExactly365Days_success() {
            ScheduledTransfer s = register(NOW.toLocalDate().plusDays(365));

            assertThat(s.getStatus()).isEqualTo(ScheduledTransferStatus.WAITING);
        }

        @Test
        @DisplayName("예약일자가 365일을 초과하면 SCD0001")
        void scheduledDateExceeds365Days_throwsInvalidScheduledDate() {
            assertThatThrownBy(() -> register(NOW.toLocalDate().plusDays(366)))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                            .isEqualTo(ScheduledTransferErrorCode.INVALID_SCHEDULED_DATE));
        }

        @Test
        @DisplayName("금액이 0 이하면 SCD0005 (Command 우회 방어)")
        void invalidAmount_throwsInvalidAmount() {
            assertThatThrownBy(() -> ScheduledTransfer.register(
                    1L, 2L, PAYEE_BANK_CODE, "110987654321", "홍길동",
                    0L, LocalDate.of(2025, 6, 2), "내메모", "받는메모", NOW))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                            .isEqualTo(ScheduledTransferErrorCode.INVALID_AMOUNT));
        }
    }

    @Nested
    @DisplayName("cancel()")
    class Cancel {

        private ScheduledTransfer withStatus(ScheduledTransferStatus status, LocalDate scheduledDate) {
            return ScheduledTransfer.reconstitute(
                    1L, 1L, 2L, PAYEE_BANK_CODE, "110987654321", "홍길동",
                    10_000L, scheduledDate, "내메모", "받는메모", status,
                    null, NOW, null, null, null);
        }

        @Test
        @DisplayName("WAITING이고 예정일 전이면 CANCELED로 전환되고 canceledAt이 기록된다")
        void success() {
            ScheduledTransfer s = withStatus(ScheduledTransferStatus.WAITING, LocalDate.of(2025, 6, 2));
            LocalDateTime cancelTime = LocalDateTime.of(2025, 6, 1, 10, 0);

            s.cancel(cancelTime);

            assertThat(s.getStatus()).isEqualTo(ScheduledTransferStatus.CANCELED);
            assertThat(s.getCanceledAt()).isEqualTo(cancelTime);
        }

        @Test
        @DisplayName("WAITING이 아니면(PROCESSING) SCD0302를 던진다")
        void notWaiting_throwsNotInWaitingStatus() {
            ScheduledTransfer s = withStatus(ScheduledTransferStatus.PROCESSING, LocalDate.of(2025, 6, 2));

            assertThatThrownBy(() -> s.cancel(LocalDateTime.of(2025, 6, 1, 10, 0)))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                            .isEqualTo(ScheduledTransferErrorCode.NOT_IN_WAITING_STATUS));
        }

        @Test
        @DisplayName("이미 CANCELED면 SCD0302를 던진다 (멱등 처리는 애플리케이션 계층 책임)")
        void alreadyCanceled_throwsNotInWaitingStatus() {
            ScheduledTransfer s = withStatus(ScheduledTransferStatus.CANCELED, LocalDate.of(2025, 6, 2));

            assertThatThrownBy(() -> s.cancel(LocalDateTime.of(2025, 6, 1, 10, 0)))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                            .isEqualTo(ScheduledTransferErrorCode.NOT_IN_WAITING_STATUS));
        }

        @Test
        @DisplayName("예정일 당일이면 SCD0303을 던진다")
        void onScheduledDate_throwsCannotCancelOnExecutionDate() {
            ScheduledTransfer s = withStatus(ScheduledTransferStatus.WAITING, LocalDate.of(2025, 6, 1));

            assertThatThrownBy(() -> s.cancel(LocalDateTime.of(2025, 6, 1, 10, 0)))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                            .isEqualTo(ScheduledTransferErrorCode.CANNOT_CANCEL_ON_EXECUTION_DATE));
        }

        @Test
        @DisplayName("예정일 전일 23:59:59는 취소 가능하다 (경계값)")
        void dayBeforeScheduledDate_success() {
            ScheduledTransfer s = withStatus(ScheduledTransferStatus.WAITING, LocalDate.of(2025, 6, 2));

            s.cancel(LocalDateTime.of(2025, 6, 1, 23, 59, 59));

            assertThat(s.getStatus()).isEqualTo(ScheduledTransferStatus.CANCELED);
        }
    }
}
