package com.shinhan.corebank.transfer.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.shinhan.corebank.IntegrationTestSupport;
import com.shinhan.corebank.transfer.adapter.out.persistence.TransferTestFixtures;
import com.shinhan.corebank.transfer.api.TransferHoldDecision;
import com.shinhan.corebank.transfer.application.port.in.TransferHistorySort;
import com.shinhan.corebank.transfer.application.port.in.TransferHistoryStatus;
import com.shinhan.corebank.transfer.application.port.out.TransferHistoryQueryPort;
import com.shinhan.corebank.transfer.application.port.out.TransferHistoryRecord;
import com.shinhan.corebank.transfer.application.port.out.TransferHoldPort;
import com.shinhan.corebank.transfer.domain.TransferChannel;
import com.shinhan.corebank.transfer.domain.TransferHold;
import jakarta.persistence.EntityManager;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@DisplayName("FDS 보류 상태 전이·이체내역 통합 테스트")
class TransferHistoryStateTransitionIntegrationTest extends IntegrationTestSupport {

    private static final long CUSTOMER_ID = 1L;
    private static final long ADMIN_ID = 9_772L;
    private static final long WITHDRAWAL_ACCOUNT_ID = 101L;

    @Autowired
    private TransferHoldService transferHoldService;

    @Autowired
    private TransferHoldPort transferHoldPort;

    @Autowired
    private TransferHistoryQueryPort transferHistoryQueryPort;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private Clock clock;

    @BeforeEach
    void seed() {
        cleanUp();
        new TransactionTemplate(transactionManager)
                .executeWithoutResult(status -> TransferTestFixtures.seedCustomerAndAccounts(entityManager));
    }

    @AfterEach
    void cleanUp() {
        jdbcTemplate.update("DELETE FROM ledger_entry WHERE account_id IN (101, 202)");
        jdbcTemplate.update("DELETE FROM transfer WHERE withdrawal_account_id = 101 AND deposit_account_id = 202");
        jdbcTemplate.update("UPDATE account SET balance = 100000, status = 'ACTIVE' WHERE account_id IN (101, 202)");
        jdbcTemplate.update("DELETE FROM transfer_limit_daily_usage WHERE customer_id = 1");
        jdbcTemplate.update("DELETE FROM transfer_hold WHERE customer_id = 1");
    }

    @Test
    @DisplayName("정책 A·B 보류를 실제 거절·승인하면 REJECTED·SUCCESS·ERROR 한 건으로 수렴한다 (#1022)")
    void policyHolds_afterActualTransitions_convergeInHistory() {
        long rejected = save("A", 10_000L);
        long succeeded = save("B", 20_000L);

        transferHoldService.reject(rejected, ADMIN_ID, "CUSTOMER_DENIED");
        transferHoldService.release(succeeded, ADMIN_ID);

        jdbcTemplate.update("UPDATE account SET balance = 1000 WHERE account_id = 101");
        long failed = save("A", 30_000L);
        transferHoldService.release(failed, ADMIN_ID);

        LocalDate today = LocalDate.now(clock);
        Page<TransferHistoryRecord> history = transferHistoryQueryPort.search(
                WITHDRAWAL_ACCOUNT_ID, null, today, today, TransferHistorySort.LATEST, PageRequest.of(0, 10));

        assertHistory(history, rejected, TransferHistoryStatus.REJECTED, false, null);
        assertHistory(history, succeeded, TransferHistoryStatus.SUCCESS, true, null);
        assertHistory(history, failed, TransferHistoryStatus.ERROR, true, "LMT0001");
        assertThat(history.getContent())
                .extracting(TransferHistoryRecord::historyId)
                .containsOnlyOnce("HOLD:" + rejected, "HOLD:" + succeeded, "HOLD:" + failed);
    }

    private long save(String policyCode, long amount) {
        LocalDateTime heldAt = LocalDateTime.now(clock);
        return transferHoldPort
                .save(TransferHold.hold(
                        CUSTOMER_ID,
                        WITHDRAWAL_ACCOUNT_ID,
                        "110222222222",
                        amount,
                        null,
                        null,
                        TransferChannel.WB,
                        null,
                        TransferHoldDecision.holdForApproval(policyCode),
                        heldAt,
                        heldAt))
                .getTransferHoldId();
    }

    private void assertHistory(
            Page<TransferHistoryRecord> history,
            long holdId,
            TransferHistoryStatus status,
            boolean hasTransactionNumber,
            String errorCode) {
        assertThat(history.getContent())
                .filteredOn(item -> item.historyId().equals("HOLD:" + holdId))
                .singleElement()
                .satisfies(item -> {
                    assertThat(item.status()).isEqualTo(status);
                    if (hasTransactionNumber) {
                        assertThat(item.transactionNumber()).isNotNull();
                    } else {
                        assertThat(item.transactionNumber()).isNull();
                    }
                    assertThat(item.errorCode()).isEqualTo(errorCode);
                });
    }
}
