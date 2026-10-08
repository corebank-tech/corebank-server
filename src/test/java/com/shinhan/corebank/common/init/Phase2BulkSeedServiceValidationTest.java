package com.shinhan.corebank.common.init;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;

class Phase2BulkSeedServiceValidationTest {

    @Test
    @DisplayName("PH-60 선행 데이터가 없으면 대량 시드 실행을 거부한다")
    void rejectsMissingMinimumSeed() {
        Phase2BulkSeedService service = service(new RecordingJdbcTemplate(0));

        assertThatIllegalStateException()
                .isThrownBy(service::validatePrerequisites)
                .withMessageContaining("PH-60 customer prerequisite");
    }

    @Test
    @DisplayName("필수 상품이 없으면 대량 시드 실행 전에 원인을 알린다")
    void rejectsMissingProduct() {
        Phase2BulkSeedService service = service(new RecordingJdbcTemplate(10_000, 30_000, 235_000, 1));

        assertThatIllegalStateException()
                .isThrownBy(service::validatePrerequisites)
                .withMessageContaining("PRD_BASIC_DEP")
                .withCauseInstanceOf(EmptyResultDataAccessException.class);
    }

    @Test
    @DisplayName("PH-60 선행 데이터와 필수 상품이 모두 있으면 사전 검증을 통과한다")
    void acceptsCompletePrerequisites() {
        Phase2BulkSeedService service = service(new ProductJdbcTemplate(10_000, 30_000, 235_000, 1));

        assertThatCode(service::validatePrerequisites).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("완료 단계는 건너뛰고 부분 단계는 재실행을 거부한다")
    void enforcesStageResumePolicy() {
        Phase2BulkSeedService service = service(new RecordingJdbcTemplate());
        AtomicBoolean inserted = new AtomicBoolean();

        service.runStage("customers", 10, 10, () -> inserted.set(true));
        assertThat(inserted).isFalse();
        assertThatIllegalStateException()
                .isThrownBy(() -> service.runStage("customers", 10, 5, () -> inserted.set(true)))
                .withMessageContaining("partial");
    }

    @Test
    @DisplayName("모든 업무 단계가 완료된 시드는 재실행을 거부한다")
    void rejectsCompletedSeed() {
        Phase2BulkSeedSpec spec = Phase2BulkSeedSpec.production();
        Phase2BulkSeedService service = service(new RecordingJdbcTemplate(
                spec.customerCount(),
                spec.accountCount(),
                spec.transferCount(),
                spec.subscriptionCount(),
                spec.autoTransferCount(),
                spec.scheduledTransferCount()));

        assertThatIllegalStateException()
                .isThrownBy(() -> service.rejectCompletedSeed(spec))
                .withMessageContaining("already complete");
    }

    @Test
    @DisplayName("완료되지 않은 시드는 중단 지점부터 재개할 수 있다")
    void acceptsIncompleteSeedForResume() {
        Phase2BulkSeedSpec spec = Phase2BulkSeedSpec.production();
        Phase2BulkSeedService service =
                service(new RecordingJdbcTemplate(spec.customerCount(), spec.accountCount(), 50_000));

        assertThatCode(() -> service.rejectCompletedSeed(spec)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("각 후속 업무 단계가 하나라도 미완료면 재개를 허용한다")
    void acceptsEveryIncompleteBusinessStageForResume() {
        Phase2BulkSeedSpec spec = Phase2BulkSeedSpec.production();
        List<RecordingJdbcTemplate> incompleteStages = List.of(
                new RecordingJdbcTemplate(spec.customerCount(), 0),
                new RecordingJdbcTemplate(spec.customerCount(), spec.accountCount(), spec.transferCount(), 0),
                new RecordingJdbcTemplate(
                        spec.customerCount(), spec.accountCount(), spec.transferCount(), spec.subscriptionCount(), 0),
                new RecordingJdbcTemplate(
                        spec.customerCount(),
                        spec.accountCount(),
                        spec.transferCount(),
                        spec.subscriptionCount(),
                        spec.autoTransferCount(),
                        0));

        for (RecordingJdbcTemplate jdbc : incompleteStages) {
            assertThatCode(() -> service(jdbc).rejectCompletedSeed(spec)).doesNotThrowAnyException();
        }
    }

    @Test
    @DisplayName("원장 시간순 잔액 정규화는 5천 계좌씩 원장·이체·계좌를 갱신한다")
    void normalizesChronologicalBalances() {
        RecordingJdbcTemplate jdbc = new RecordingJdbcTemplate();
        Phase2BulkSeedService service = service(jdbc);

        service.normalizeBalancesChronologically(Phase2BulkSeedSpec.production());

        assertThat(jdbc.updateCount).isEqualTo(108);
    }

    @Test
    @DisplayName("자동이체 최초 실행일은 시작일 당일 이후 처음 도래하는 지정 이체일이다")
    void calculatesFirstAutoTransferExecutionDate() {
        Phase2BulkSeedService service = service(new RecordingJdbcTemplate());

        assertThat(service.firstExecutionDate(LocalDate.of(2026, 10, 13), 20)).isEqualTo(LocalDate.of(2026, 10, 20));
        assertThat(service.firstExecutionDate(LocalDate.of(2026, 10, 13), 1)).isEqualTo(LocalDate.of(2026, 11, 1));
    }

    @Test
    @DisplayName("구간 체크포인트는 완료 또는 청크 경계만 허용한다")
    void validatesChunkCheckpoint() {
        Phase2BulkSeedService service = service(new RecordingJdbcTemplate());

        assertThatCode(() -> service.validateCompletedChunkCount("transfer", 50, 100, 50))
                .doesNotThrowAnyException();
        assertThatIllegalStateException()
                .isThrownBy(() -> service.validateCompletedChunkCount("transfer", 51, 100, 50))
                .withMessageContaining("checkpoint");
        assertThatIllegalStateException()
                .isThrownBy(() -> service.validateCompletedChunkCount("transfer", -1, 100, 50))
                .withMessageContaining("checkpoint");
        assertThatIllegalStateException()
                .isThrownBy(() -> service.validateCompletedChunkCount("transfer", 101, 100, 50))
                .withMessageContaining("checkpoint");
        assertThatCode(() -> service.validateCompletedChunkCount("transfer", 100, 100, 30))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("출금 후 음수 잔액과 누락 계좌를 명시적으로 거부한다")
    void rejectsInvalidBalances() {
        Phase2BulkSeedService service = service(new RecordingJdbcTemplate());

        assertThat(service.withdrawalBalanceAfter(10_000, 4_000, 1, "이체")).isEqualTo(6_000);
        assertThatIllegalStateException()
                .isThrownBy(() -> service.withdrawalBalanceAfter(1_000, 2_000, 1, "이체"))
                .withMessageContaining("잔액이 부족");
        assertThatIllegalStateException()
                .isThrownBy(() -> service.balance(Map.of(), 1))
                .withMessageContaining("대상 계좌");
    }

    @Test
    @DisplayName("개시 전표 합계와 전표번호 채번 한계를 검증한다")
    void validatesOpeningBalanceAndVoucherSequence() {
        Phase2BulkSeedService service = service(new RecordingJdbcTemplate());

        assertThatCode(() -> service.validateOpeningBalances(1_000, 1_000)).doesNotThrowAnyException();
        assertThatIllegalStateException()
                .isThrownBy(() -> service.validateOpeningBalances(1_000, 999))
                .withMessageContaining("OPENING");
        assertThat(service.voucherNumber(LocalDate.of(2026, 9, 1), "TRF", 999_999))
                .isEqualTo("20260901-TRF-999999");
        assertThatIllegalStateException()
                .isThrownBy(() -> service.voucherNumber(LocalDate.of(2026, 9, 1), "TRF", 1_000_000))
                .withMessageContaining("6자리");
    }

    @Test
    @DisplayName("상품 금리 누락과 ID 체크포인트 누락을 감지한다")
    void rejectsMissingRateAndRangeGap() {
        Phase2BulkSeedService missingRate = service(new RecordingJdbcTemplate());
        Phase2BulkSeedService gap = service(new RangeJdbcTemplate(new Phase2BulkSeedService.RangeProgress(10, 12, 2)));
        Phase2BulkSeedService contiguous =
                service(new RangeJdbcTemplate(new Phase2BulkSeedService.RangeProgress(10, 11, 2)));
        Phase2BulkSeedService wrongMinimum =
                service(new RangeJdbcTemplate(new Phase2BulkSeedService.RangeProgress(11, 12, 2)));

        assertThatIllegalStateException()
                .isThrownBy(() -> missingRate.rate(1, 12))
                .withMessageContaining("상품 기간 금리");
        assertThatIllegalStateException()
                .isThrownBy(() -> gap.rangeCount("transfer", "transfer_id", 10, 20))
                .withMessageContaining("누락된 체크포인트");
        assertThatIllegalStateException()
                .isThrownBy(() -> wrongMinimum.rangeCount("transfer", "transfer_id", 10, 20))
                .withMessageContaining("누락된 체크포인트");
        assertThat(contiguous.rangeCount("transfer", "transfer_id", 10, 20)).isEqualTo(2);
    }

    @Test
    @DisplayName("null 집계값과 빈 ID 대역은 0으로 처리한다")
    void treatsNullAggregatesAsZero() {
        Phase2BulkSeedService service = service(new NullJdbcTemplate());

        assertThat(service.count("SELECT 1")).isZero();
        assertThat(service.longValue("SELECT 1")).isZero();
        assertThat(service.rangeCount("transfer", "transfer_id", 10, 20)).isZero();
    }

    @Test
    @DisplayName("PH-60 전표 보완은 빈 대역에서 전표와 분개를 함께 생성한다")
    void backfillsMinimumSeedVouchers() {
        RecordingJdbcTemplate jdbc = new RecordingJdbcTemplate(0, 0);
        Phase2BulkSeedService service = service(jdbc);

        service.backfillPhase60Gl(Phase2BulkSeedSpec.production());

        org.assertj.core.api.Assertions.assertThat(jdbc.updateCount).isEqualTo(2);
    }

    @Test
    @DisplayName("PH-60 전표 보완이 일부만 존재하면 재실행을 거부한다")
    void rejectsPartialMinimumSeedBackfill() {
        Phase2BulkSeedService service = service(new RecordingJdbcTemplate(1, 0));

        assertThatIllegalStateException()
                .isThrownBy(() -> service.backfillPhase60Gl(Phase2BulkSeedSpec.production()))
                .withMessageContaining("부분 적재");
    }

    @Test
    @DisplayName("PH-60 전표와 분개 중 어느 한쪽만 존재해도 부분 적재로 거부한다")
    void rejectsEachPartialMinimumSeedBackfillShape() {
        Phase2BulkSeedSpec spec = Phase2BulkSeedSpec.production();

        assertThatIllegalStateException()
                .isThrownBy(() -> service(new RecordingJdbcTemplate(235_000, 0)).backfillPhase60Gl(spec))
                .withMessageContaining("부분 적재");
        assertThatIllegalStateException()
                .isThrownBy(() -> service(new RecordingJdbcTemplate(0, 1)).backfillPhase60Gl(spec))
                .withMessageContaining("부분 적재");
    }

    @Test
    @DisplayName("완료된 PH-60 전표 보완은 다시 생성하지 않는다")
    void skipsCompletedMinimumSeedBackfill() {
        RecordingJdbcTemplate jdbc = new RecordingJdbcTemplate(235_000, 470_000);
        Phase2BulkSeedService service = service(jdbc);

        service.backfillPhase60Gl(Phase2BulkSeedSpec.production());

        org.assertj.core.api.Assertions.assertThat(jdbc.updateCount).isZero();
    }

    @Test
    @DisplayName("적재 완료 후 모든 자동 채번을 전용 대역 다음으로 이동한다")
    void advancesSequences() {
        RecordingJdbcTemplate jdbc = new RecordingJdbcTemplate();
        Phase2BulkSeedService service = service(jdbc);

        service.advanceSequences(Phase2BulkSeedSpec.production());

        org.assertj.core.api.Assertions.assertThat(jdbc.executeCount).isEqualTo(7);
        org.assertj.core.api.Assertions.assertThat(jdbc.updateCount).isEqualTo(5);
    }

    private Phase2BulkSeedService service(RecordingJdbcTemplate jdbc) {
        return new Phase2BulkSeedService(
                jdbc,
                new NoOpTransactionManager(),
                Clock.fixed(Instant.parse("2026-10-07T00:00:00Z"), ZoneId.of("Asia/Seoul")));
    }

    private static class RecordingJdbcTemplate extends JdbcTemplate {

        private final Queue<Integer> counts;
        private int updateCount;
        private int executeCount;

        private RecordingJdbcTemplate(Integer... counts) {
            this.counts = new ArrayDeque<>(Arrays.asList(counts));
        }

        @Override
        public <T> T queryForObject(String sql, Class<T> requiredType, Object... args) {
            if (counts.isEmpty()) {
                throw new EmptyResultDataAccessException(1);
            }
            return requiredType.cast(counts.remove());
        }

        @Override
        public <T> T queryForObject(String sql, RowMapper<T> rowMapper, Object... args) {
            throw new EmptyResultDataAccessException(1);
        }

        @Override
        public int update(String sql, Object... args) {
            updateCount++;
            return 1;
        }

        @Override
        public int update(String sql) {
            updateCount++;
            return 1;
        }

        @Override
        public void execute(String sql) {
            executeCount++;
        }
    }

    private static final class ProductJdbcTemplate extends RecordingJdbcTemplate {

        private ProductJdbcTemplate(Integer... counts) {
            super(counts);
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> T queryForObject(String sql, RowMapper<T> rowMapper, Object... args) {
            return (T) new Phase2BulkSeedService.ProductSeed(1, String.valueOf(args[0]));
        }
    }

    private static final class RangeJdbcTemplate extends RecordingJdbcTemplate {

        private final Phase2BulkSeedService.RangeProgress progress;

        private RangeJdbcTemplate(Phase2BulkSeedService.RangeProgress progress) {
            this.progress = progress;
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> T queryForObject(String sql, RowMapper<T> rowMapper, Object... args) {
            return (T) progress;
        }
    }

    private static final class NullJdbcTemplate extends RecordingJdbcTemplate {

        @Override
        public <T> T queryForObject(String sql, Class<T> requiredType, Object... args) {
            return null;
        }

        @Override
        public <T> T queryForObject(String sql, RowMapper<T> rowMapper, Object... args) {
            return null;
        }
    }

    private static final class NoOpTransactionManager implements PlatformTransactionManager {

        @Override
        public TransactionStatus getTransaction(TransactionDefinition definition) {
            return new SimpleTransactionStatus();
        }

        @Override
        public void commit(TransactionStatus status) {}

        @Override
        public void rollback(TransactionStatus status) {}
    }
}
