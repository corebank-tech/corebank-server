package com.shinhan.corebank.notification.adapter.in.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import com.shinhan.corebank.IntegrationTestSupport;
import com.shinhan.corebank.account.api.AccountPasswordAuthTokenVerifier;
import com.shinhan.corebank.common.domain.ProcessResultStatus;
import com.shinhan.corebank.notification.application.port.out.NotificationCommandPort;
import com.shinhan.corebank.otp.api.OtpAuthTokenVerifier;
import com.shinhan.corebank.transfer.adapter.out.persistence.TransferTestFixtures;
import com.shinhan.corebank.transfer.api.TransferSettled;
import com.shinhan.corebank.transfer.application.port.in.TransferCommand;
import com.shinhan.corebank.transfer.application.port.in.TransferExecutionUseCase;
import com.shinhan.corebank.transfer.application.port.in.TransferResult;
import com.shinhan.corebank.transfer.domain.TransferChannel;
import com.shinhan.corebank.transfer.domain.TransferType;
import jakarta.persistence.EntityManager;
import java.time.Duration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 이체와 발행 기록이 함께 커밋된 뒤 알림 저장이 실패해도 이체는 그대로이고, 발행 기록이 남아 재제출 대상이 되는지
 * 실제 이체로 확인한다(#706 인수기준, PR #723 리뷰).
 */
class NotificationFailureIsolationTest extends IntegrationTestSupport {

    private static final long WITHDRAWAL_ACCOUNT_ID = 101L;
    private static final long INITIAL_BALANCE = 100_000L;
    private static final long AMOUNT = 30_000L;
    private static final Duration LISTENER_TIMEOUT = Duration.ofSeconds(10);

    // 이체 실행에 필요한 인증 경계만 대역으로 둔다 — 이체 통합 테스트와 같은 구성
    @MockitoBean
    private OtpAuthTokenVerifier otpAuthTokenVerifier;

    @MockitoBean
    private AccountPasswordAuthTokenVerifier accountPasswordAuthTokenVerifier;

    // 알림 저장이 DB 장애로 실패하는 상황을 만든다
    @MockitoBean
    private NotificationCommandPort notificationCommandPort;

    @Autowired
    private TransferExecutionUseCase transferExecutionUseCase;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @AfterEach
    void cleanUp() {
        jdbcTemplate.update(
                "DELETE FROM EVENT_PUBLICATION WHERE EVENT_TYPE = ? AND SERIALIZED_EVENT LIKE ?",
                TransferSettled.class.getName(),
                "%\"customerId\":1,%");
        jdbcTemplate.update("DELETE FROM ledger_entry WHERE account_id IN (101, 202)");
        jdbcTemplate.update("DELETE FROM transfer WHERE withdrawal_account_id = 101 AND deposit_account_id = 202");
        jdbcTemplate.update("UPDATE account SET balance = 100000, status = 'ACTIVE' WHERE account_id IN (101, 202)");
        jdbcTemplate.update("DELETE FROM transfer_limit_daily_usage WHERE customer_id = 1");
    }

    @Test
    @DisplayName("이체가 커밋된 뒤 알림 저장이 실패해도 이체 · 원장 · 잔액은 그대로이고, 발행 기록이 남아 재제출 대상이 된다")
    void notificationSaveFailsAfterTransferCommit_keepsTransferAndLeavesPublication() {
        // given
        new TransactionTemplate(transactionManager)
                .executeWithoutResult(status -> TransferTestFixtures.seedCustomerAndAccounts(entityManager));
        doThrow(new DataAccessResourceFailureException("알림 저장소 장애"))
                .when(notificationCommandPort)
                .saveIfAbsent(any());

        // when
        TransferResult result = transferExecutionUseCase.execute(immediateTransfer());

        // then — 커밋 뒤 리스너가 알림 저장을 시도했다가 실패한다
        verify(notificationCommandPort, timeout(LISTENER_TIMEOUT.toMillis())).saveIfAbsent(any());
        assertThat(result.status()).isEqualTo(ProcessResultStatus.SUCCESS);
        assertThat(transferStatus(result.transactionNumber())).isEqualTo("SUCCESS");
        assertThat(ledgerRowCount(result.transactionNumber())).isEqualTo(2);
        assertThat(balance(WITHDRAWAL_ACCOUNT_ID)).isEqualTo(INITIAL_BALANCE - AMOUNT);
        assertThat(notificationCount(result.transactionNumber())).isZero();
        await().atMost(LISTENER_TIMEOUT)
                .untilAsserted(() -> assertThat(incompleteNotificationPublicationCount(result.transactionNumber()))
                        .isEqualTo(1));
    }

    private static TransferCommand immediateTransfer() {
        return TransferCommand.builder()
                .customerId(1L)
                .authToken("dummy-auth-token")
                .otpAuthToken("dummy-otp-token")
                .withdrawalAccountId(WITHDRAWAL_ACCOUNT_ID)
                .depositAccountNumber("110222222222")
                .amount(AMOUNT)
                .transferType(TransferType.IMMEDIATE)
                .channel(TransferChannel.WB)
                .build();
    }

    private String transferStatus(String transactionNumber) {
        return jdbcTemplate.queryForObject(
                "SELECT status FROM transfer WHERE transaction_number = ?", String.class, transactionNumber);
    }

    private int ledgerRowCount(String transactionNumber) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM ledger_entry WHERE transaction_number = ?", Integer.class, transactionNumber);
    }

    private long balance(long accountId) {
        return jdbcTemplate.queryForObject("SELECT balance FROM account WHERE account_id = ?", Long.class, accountId);
    }

    private int notificationCount(String transactionNumber) {
        return jdbcTemplate.queryForObject(
                """
            SELECT COUNT(*) FROM notification n JOIN transfer t ON n.ref_id = t.transfer_id
            WHERE n.type = 'TRANSFER' AND t.transaction_number = ?
            """,
                Integer.class,
                transactionNumber);
    }

    // 완료 기록이 없는 알림 리스너의 발행 기록 — 재제출(IncompleteEventPublications)이 다시 보낼 대상이다
    private int incompleteNotificationPublicationCount(String transactionNumber) {
        Long transferId = jdbcTemplate.queryForObject(
                "SELECT transfer_id FROM transfer WHERE transaction_number = ?", Long.class, transactionNumber);
        return jdbcTemplate.queryForObject(
                """
            SELECT COUNT(*) FROM EVENT_PUBLICATION
            WHERE LISTENER_ID LIKE ? AND COMPLETION_DATE IS NULL AND SERIALIZED_EVENT LIKE ?
            """,
                Integer.class,
                "%NotificationRequestedListener%",
                "%\"refId\":" + transferId + ",%");
    }
}
