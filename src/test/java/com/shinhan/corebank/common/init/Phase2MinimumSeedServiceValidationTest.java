package com.shinhan.corebank.common.init;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import java.time.LocalDateTime;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Queue;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

class Phase2MinimumSeedServiceValidationTest {

    private static final Phase2MinimumSeedSpec SPEC = new Phase2MinimumSeedSpec(
            2,
            6,
            8,
            0,
            0,
            8_100_001L,
            81_000_001L,
            81_000_001L,
            81_000_001L,
            81_000_001L,
            8_100_000_001L,
            "861",
            LocalDateTime.of(2026, 9, 1, 0, 0));

    @Test
    @DisplayName("시드의 고정 건수가 다르면 검증을 중단한다")
    void rejectsCountMismatch() {
        Phase2MinimumSeedService service = serviceWithCounts(1);

        assertThatIllegalStateException()
                .isThrownBy(() -> service.validate(SPEC))
                .withMessageContaining("customers count mismatch");
    }

    @Test
    @DisplayName("원장 쌍이 깨지면 검증을 중단한다")
    void rejectsUnpairedLedger() {
        Phase2MinimumSeedService service = serviceWithCounts(successCountsWith(13, 1));

        assertThatIllegalStateException()
                .isThrownBy(() -> service.validate(SPEC))
                .withMessageContaining("원장 쌍 정합성");
    }

    @Test
    @DisplayName("계좌 잔액과 원장 합계가 다르면 검증을 중단한다")
    void rejectsBalanceMismatch() {
        Phase2MinimumSeedService service = serviceWithCounts(successCountsWith(14, 1));

        assertThatIllegalStateException()
                .isThrownBy(() -> service.validate(SPEC))
                .withMessageContaining("계좌 잔액 정합성");
    }

    @Test
    @DisplayName("계좌 마지막 거래 시각과 원장이 다르면 검증을 중단한다")
    void rejectsLastTransactionMismatch() {
        Phase2MinimumSeedService service = serviceWithCounts(successCountsWith(15, 1));

        assertThatIllegalStateException()
                .isThrownBy(() -> service.validate(SPEC))
                .withMessageContaining("마지막 거래 시각");
    }

    @Test
    @DisplayName("개시 전표 차대변이 다르면 검증을 중단한다")
    void rejectsUnbalancedOpeningVoucher() {
        Phase2MinimumSeedService service = serviceWithCounts(successCountsWith(16, 1));

        assertThatIllegalStateException()
                .isThrownBy(() -> service.validate(SPEC))
                .withMessageContaining("차대변");
    }

    @Test
    @DisplayName("생성 결과에 음수 잔액이 있으면 적재를 중단한다")
    void rejectsNegativeBalance() {
        Phase2MinimumSeedService service = new Phase2MinimumSeedService(null);

        assertThatIllegalStateException()
                .isThrownBy(() -> service.validateNonNegativeBalances(new long[] {1L, -1L}))
                .withMessageContaining("음수 잔액");
    }

    @Test
    @DisplayName("집계 결과가 null이면 0건으로 처리해 고정 건수 불일치를 감지한다")
    void treatsNullCountAsZero() {
        Phase2MinimumSeedService service = new Phase2MinimumSeedService(new NullJdbcTemplate());

        assertThatIllegalStateException()
                .isThrownBy(() -> service.validate(SPEC))
                .withMessageContaining("customers count mismatch");
    }

    @Test
    @DisplayName("필수 상품이 없으면 상품 코드를 포함한 오류를 반환한다")
    void rejectsMissingProduct() {
        Phase2MinimumSeedService service = new Phase2MinimumSeedService(new QueueJdbcTemplate());

        assertThatIllegalStateException()
                .isThrownBy(() -> service.productId("MISSING_PRODUCT"))
                .withMessageContaining("MISSING_PRODUCT")
                .withCauseInstanceOf(EmptyResultDataAccessException.class);
    }

    @Test
    @DisplayName("입금 고객이 출금 고객과 같으면 다음 고객으로 보정한다")
    void avoidsSelfTransfer() {
        Phase2MinimumSeedService service = new Phase2MinimumSeedService(null);

        assertThat(service.depositCustomerIndex(0, 4)).isEqualTo(1);
        assertThat(service.depositCustomerIndex(4, 5)).isZero();
    }

    private Phase2MinimumSeedService serviceWithCounts(Integer... counts) {
        return new Phase2MinimumSeedService(new QueueJdbcTemplate(counts));
    }

    private Integer[] successCountsWith(int index, int value) {
        Integer[] counts = {2, 6, 8, 0, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 2};
        counts[index] = value;
        return counts;
    }

    private static class NullJdbcTemplate extends JdbcTemplate {

        @Override
        public <T> T queryForObject(String sql, Class<T> requiredType, Object... args) {
            return null;
        }
    }

    private static class QueueJdbcTemplate extends JdbcTemplate {

        private final Queue<Integer> counts;

        QueueJdbcTemplate(Integer... counts) {
            this.counts = new ArrayDeque<>(Arrays.asList(counts));
        }

        @Override
        public <T> T queryForObject(String sql, Class<T> requiredType, Object... args) {
            if (requiredType == Long.class) {
                throw new EmptyResultDataAccessException(1);
            }
            return requiredType.cast(counts.remove());
        }
    }
}
