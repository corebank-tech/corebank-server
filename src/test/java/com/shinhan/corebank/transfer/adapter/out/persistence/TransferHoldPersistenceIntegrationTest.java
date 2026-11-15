package com.shinhan.corebank.transfer.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.shinhan.corebank.IntegrationTestSupport;
import com.shinhan.corebank.common.exception.BusinessException;
import com.shinhan.corebank.transfer.api.TransferHoldDecision;
import com.shinhan.corebank.transfer.application.port.out.TransferHoldPort;
import com.shinhan.corebank.transfer.domain.TransferChannel;
import com.shinhan.corebank.transfer.domain.TransferHold;
import com.shinhan.corebank.transfer.domain.TransferHoldStatus;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/** 스레드마다 따로 커밋해야 동시 전이를 볼 수 있어 @Transactional을 두지 않고 직접 지운다. */
@DisplayName("이체 보류 저장소(TransferHoldPort) 통합 테스트")
class TransferHoldPersistenceIntegrationTest extends IntegrationTestSupport {

    // 시드와 다른 테스트가 쓰지 않는 고객 ID 대역(772_0xx). transfer_hold는 FK가 없어 계좌 시드가 필요 없다.
    private static final long CUSTOMER_ID = 772_001L;
    private static final LocalDateTime NOW = LocalDateTime.of(2031, 7, 1, 10, 0);
    private static final LocalDateTime START_OF_DAY = NOW.toLocalDate().atStartOfDay();

    @Autowired
    private TransferHoldPort port;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @AfterEach
    void cleanUp() {
        jdbcTemplate.update("DELETE FROM transfer_hold WHERE customer_id = ?", CUSTOMER_ID);
    }

    @Test
    @DisplayName("저장한 보류를 다시 읽으면 원본 요청과 판정이 그대로다")
    void save_thenFind_roundTrips() {
        TransferHold saved = port.save(newHold());

        TransferHold found = port.findById(saved.getTransferHoldId()).orElseThrow();

        assertThat(found.getStatus()).isEqualTo(TransferHoldStatus.HELD);
        assertThat(found.getCustomerId()).isEqualTo(CUSTOMER_ID);
        assertThat(found.getWithdrawalAccountId()).isEqualTo(101L);
        assertThat(found.getDepositAccountNumber()).isEqualTo("110222222222");
        assertThat(found.getAmount()).isEqualTo(1_000_000L);
        assertThat(found.getMyPassbookMemo()).isEqualTo("생활비");
        assertThat(found.getRecipientPassbookMemo()).isEqualTo("홍길동");
        assertThat(found.getChannel()).isEqualTo(TransferChannel.WB);
        assertThat(found.getFlowId()).isEqualTo("flow-772");
        assertThat(found.getPolicyCode()).isEqualTo("DELAYED");
        assertThat(found.getReleaseAt()).isEqualTo(NOW.plusHours(3));
        assertThat(found.getAuthenticatedAt()).isEqualTo(NOW);
    }

    @Test
    @DisplayName("HELD → EXECUTING → EXECUTED는 한 번씩만 되고, 같은 전이를 다시 하면 false다")
    void execute_transitionsOnce() {
        long id = port.save(newHold()).getTransferHoldId();

        assertThat(port.markDueExecuting(id, NOW.plusHours(3))).isTrue();
        assertThat(port.markDueExecuting(id, NOW.plusHours(3))).isFalse();
        assertThat(port.close(id, TransferHoldStatus.CANCELLED, CUSTOMER_ID, null, NOW.plusHours(3)))
                .isFalse();

        assertThat(port.finishExecution(
                        id, NOW.plusHours(3), TransferHoldStatus.EXECUTED, null, null, NOW.plusHours(3)))
                .isTrue();
        assertThat(port.finishExecution(
                        id, NOW.plusHours(3), TransferHoldStatus.FAILED, null, "TRF0303", NOW.plusHours(3)))
                .isFalse();

        Map<String, Object> row = row(id);
        assertThat(row.get("status")).isEqualTo("EXECUTED");
        assertThat(row.get("executing_at")).isNotNull();
        assertThat(row.get("completed_at")).isNotNull();
    }

    @Test
    @DisplayName("취소한 보류는 실행을 선점할 수 없고, 취소 주체가 남는다")
    void close_blocksExecution() {
        long id = port.save(newHold()).getTransferHoldId();

        assertThat(port.close(id, TransferHoldStatus.CANCELLED, CUSTOMER_ID, null, NOW.plusHours(1)))
                .isTrue();

        assertThat(port.markDueExecuting(id, NOW.plusHours(3))).isFalse();
        Map<String, Object> row = row(id);
        assertThat(row.get("status")).isEqualTo("CANCELLED");
        assertThat(((Number) row.get("actor_id")).longValue()).isEqualTo(CUSTOMER_ID);
    }

    @Test
    @DisplayName("실행 결과에 종료 상태가 아닌 값을, 종료에 실행 결과를 넘기면 거부한다")
    void transitions_rejectWrongResult() {
        long id = port.save(newHold()).getTransferHoldId();

        assertThatThrownBy(() -> port.finishExecution(id, NOW, TransferHoldStatus.CANCELLED, null, null, NOW))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> port.close(id, TransferHoldStatus.EXECUTED, null, null, NOW))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("같은 보류를 여러 스레드가 동시에 실행 선점해도 한 곳만 성공한다")
    void markDueExecuting_concurrently_onlyOneWins() throws Exception {
        long id = port.save(newHold()).getTransferHoldId();
        int threads = 8;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Callable<Boolean> attempt = () -> {
                start.await();
                return port.markDueExecuting(id, NOW.plusHours(3));
            };
            List<Future<Boolean>> futures = new ArrayList<>();
            for (int i = 0; i < threads; i++) {
                futures.add(pool.submit(attempt));
            }
            start.countDown();

            long wins = 0;
            for (Future<Boolean> future : futures) {
                if (future.get(10, TimeUnit.SECONDS)) {
                    wins++;
                }
            }
            assertThat(wins).isEqualTo(1);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    @DisplayName("시각으로 푸는 선점은 해제 시각이 지나야 되고, 승인 대기는 승인 선점으로만 되며 heldFrom 이후 보류만 된다")
    void markExecuting_respectsReleaseMode() {
        long atTime = port.save(newHold()).getTransferHoldId();
        long approval = saveApproval();

        assertThat(port.markDueExecuting(atTime, NOW.plusHours(3).minusSeconds(1)))
                .isFalse();
        assertThat(port.markApprovedExecuting(atTime, 9L, NOW.plusHours(3), START_OF_DAY))
                .isFalse();
        assertThat(port.markDueExecuting(approval, NOW.plusHours(3))).isFalse();
        // 보류 시각이 기준보다 이르면 지난 날 보류다(#998). 같은 시각은 포함한다
        assertThat(port.markApprovedExecuting(approval, 9L, NOW, NOW.plusNanos(1_000)))
                .isFalse();
        assertThat(port.markApprovedExecuting(approval, 9L, NOW, NOW)).isTrue();
        assertThat(port.markDueExecuting(atTime, NOW.plusHours(3))).isTrue();
    }

    @Test
    @DisplayName("[#998] 만료 대상은 heldBefore 전에 보류한 승인 대기 HELD뿐이다. 지연이체 보류는 넣지 않는다")
    void findOverdueApprovalIds_onlyApprovalHeldBefore() {
        long atTime = port.save(newHold()).getTransferHoldId();
        long approval = saveApproval();

        assertThat(port.findOverdueApprovalIds(NOW, 10)).doesNotContain(approval, atTime);
        assertThat(port.findOverdueApprovalIds(NOW.plusNanos(1_000), 10))
                .contains(approval)
                .doesNotContain(atTime);
        port.close(approval, TransferHoldStatus.REJECTED, 9L, "TEST", NOW);
        assertThat(port.findOverdueApprovalIds(NOW.plusNanos(1_000), 10)).doesNotContain(approval);
    }

    private long saveApproval() {
        return port.save(TransferHold.hold(
                        CUSTOMER_ID,
                        101L,
                        "110222222222",
                        1_000_000L,
                        null,
                        null,
                        TransferChannel.WB,
                        null,
                        TransferHoldDecision.holdForApproval("FDS_TEST"),
                        NOW,
                        NOW))
                .getTransferHoldId();
    }

    @Test
    @DisplayName("멈춘 지 오래된 EXECUTING만 다시 선점되고, 다시 선점되면 앞선 선점 표식으로는 결과를 기록할 수 없다")
    void reclaimStuck_fencesPreviousClaim() {
        long id = port.save(newHold()).getTransferHoldId();
        LocalDateTime firstClaim = NOW.plusHours(3);
        port.markDueExecuting(id, firstClaim);

        assertThat(port.reclaimStuck(id, firstClaim.minusSeconds(1), firstClaim.plusMinutes(10)))
                .isFalse();
        LocalDateTime secondClaim = firstClaim.plusMinutes(10);
        assertThat(port.reclaimStuck(id, firstClaim, secondClaim)).isTrue();

        assertThat(port.finishExecution(id, firstClaim, TransferHoldStatus.FAILED, null, "X", secondClaim))
                .isFalse();
        assertThat(port.finishExecution(id, secondClaim, TransferHoldStatus.EXECUTED, null, null, secondClaim))
                .isTrue();
        assertThat(row(id).get("status")).isEqualTo("EXECUTED");
    }

    private Map<String, Object> row(long id) {
        return jdbcTemplate.queryForMap("SELECT * FROM transfer_hold WHERE transfer_hold_id = ?", id);
    }

    private static TransferHold newHold() {
        return TransferHold.hold(
                CUSTOMER_ID,
                101L,
                "110222222222",
                1_000_000L,
                "생활비",
                "홍길동",
                TransferChannel.WB,
                "flow-772",
                TransferHoldDecision.holdUntil("DELAYED", NOW.plusHours(3)),
                NOW,
                NOW);
    }
}
