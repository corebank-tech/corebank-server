package com.shinhan.corebank.transfer.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doThrow;

import com.shinhan.corebank.IntegrationTestSupport;
import com.shinhan.corebank.account.api.AccountPasswordAuthTokenVerifier;
import com.shinhan.corebank.common.domain.ProcessResultStatus;
import com.shinhan.corebank.common.event.DomainEventSink;
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
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 이체 확정과 TransferSettled 발행 기록(EVENT_PUBLICATION)이 한 트랜잭션으로 함께 커밋되거나 함께 롤백되는지 실제 이체로
 * 확인한다(PR #723 리뷰). 발행 기록만 남거나 이체만 남는 부분 실패가 없어야 한다.
 */
class TransferSettledPublicationAtomicityTest extends IntegrationTestSupport {

    private static final long WITHDRAWAL_ACCOUNT_ID = 101L;
    private static final long DEPOSIT_ACCOUNT_ID = 202L;
    private static final long INITIAL_BALANCE = 100_000L;
    private static final long AMOUNT = 30_000L;
    // 성공 확정의 발행 기록만 DB 가 거부하게 하는 제약. 테스트는 직렬 실행이고 매 테스트 뒤에 지운다
    private static final String REJECT_SUCCESS_PUBLICATION = "chk_test_reject_success_transfer_settled";

    // 이체 실행에 필요한 인증 경계만 대역으로 둔다 — 이체 통합 테스트와 같은 구성
    @MockitoBean
    private OtpAuthTokenVerifier otpAuthTokenVerifier;

    @MockitoBean
    private AccountPasswordAuthTokenVerifier accountPasswordAuthTokenVerifier;

    // 알림 리스너가 완료되면 발행 기록이 지워지므로, 저장을 실패시켜 커밋된 기록을 그대로 관찰한다
    @MockitoBean
    private NotificationCommandPort notificationCommandPort;

    // 발행 기록 INSERT 뒤, 커밋 직전에 실패시키는 자리
    @MockitoBean
    private DomainEventSink domainEventSink;

    @Autowired
    private TransferExecutionUseCase transferExecutionUseCase;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @BeforeEach
    void setUp() {
        cleanUp();
        new TransactionTemplate(transactionManager)
                .executeWithoutResult(status -> TransferTestFixtures.seedCustomerAndAccounts(entityManager));
        doThrow(new DataAccessResourceFailureException("알림 저장소 장애"))
                .when(notificationCommandPort)
                .saveIfAbsent(any());
    }

    @AfterEach
    void cleanUp() {
        dropRejectSuccessPublicationConstraint();
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
    @DisplayName("이체가 성공하면 이체 · 원장 · 잔액과 SUCCESS 발행 기록이 함께 커밋된다")
    void transferSucceeds_commitsTransferAndPublicationTogether() {
        // when
        TransferResult result = transferExecutionUseCase.execute(immediateTransfer());

        // then
        assertThat(result.status()).isEqualTo(ProcessResultStatus.SUCCESS);
        assertThat(transferStatuses()).containsExactly("SUCCESS");
        assertThat(ledgerRowCount()).isEqualTo(2);
        assertThat(balance(WITHDRAWAL_ACCOUNT_ID)).isEqualTo(INITIAL_BALANCE - AMOUNT);
        assertThat(publicationCount(ProcessResultStatus.SUCCESS)).isEqualTo(1);
    }

    @Test
    @DisplayName("발행 기록 저장이 실패하면 이체 · 원장 · 잔액이 함께 롤백되고 SUCCESS 발행 기록도 없다")
    void publicationInsertFails_rollsBackTransfer() {
        // given — DB 가 SUCCESS 발행 기록 INSERT 만 거부하고, 뒤이은 ERROR 확정의 발행 기록은 받는다
        jdbcTemplate.execute("ALTER TABLE EVENT_PUBLICATION ADD CONSTRAINT " + REJECT_SUCCESS_PUBLICATION
                + " CHECK (NOT (EVENT_TYPE = '" + TransferSettled.class.getName() + "'"
                + " AND SERIALIZED_EVENT LIKE '%\"status\":\"SUCCESS\"%'))");

        // when · then
        assertThatThrownBy(() -> transferExecutionUseCase.execute(immediateTransfer()))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining(REJECT_SUCCESS_PUBLICATION);
        assertNoSuccessLeftBehind();
    }

    @Test
    @DisplayName("발행 기록을 남긴 뒤 커밋 직전에 실패하면 SUCCESS 발행 기록도 이체와 함께 롤백된다")
    void failsAfterPublicationInsert_rollsBackPublication() {
        // given — 성공 확정 이벤트를 커밋 직전에 넘기다 실패한다. 발행 기록은 이미 같은 트랜잭션에 INSERT 된 뒤다
        doThrow(new IllegalStateException("커밋 직전 실패"))
                .when(domainEventSink)
                .record(argThat(event ->
                        event instanceof TransferSettled settled && settled.status() == ProcessResultStatus.SUCCESS));

        // when · then
        assertThatThrownBy(() -> transferExecutionUseCase.execute(immediateTransfer()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("커밋 직전 실패");
        assertNoSuccessLeftBehind();
    }

    // 성공 확정의 흔적은 이체 쪽에도 발행 기록 쪽에도 없고, 남은 ERROR 확정은 자기 발행 기록과 함께 커밋됐다
    private void assertNoSuccessLeftBehind() {
        assertThat(balance(WITHDRAWAL_ACCOUNT_ID)).isEqualTo(INITIAL_BALANCE);
        assertThat(balance(DEPOSIT_ACCOUNT_ID)).isEqualTo(INITIAL_BALANCE);
        assertThat(ledgerRowCount()).isZero();
        assertThat(publicationCount(ProcessResultStatus.SUCCESS)).isZero();
        assertThat(transferStatuses()).containsExactly("ERROR");
        assertThat(publicationCount(ProcessResultStatus.ERROR)).isEqualTo(1);
    }

    private void dropRejectSuccessPublicationConstraint() {
        Integer constraints = jdbcTemplate.queryForObject(
                """
            SELECT COUNT(*) FROM information_schema.TABLE_CONSTRAINTS
            WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'EVENT_PUBLICATION' AND CONSTRAINT_NAME = ?
            """,
                Integer.class,
                REJECT_SUCCESS_PUBLICATION);
        if (constraints > 0) {
            jdbcTemplate.execute("ALTER TABLE EVENT_PUBLICATION DROP CHECK " + REJECT_SUCCESS_PUBLICATION);
        }
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

    private List<String> transferStatuses() {
        return jdbcTemplate.queryForList(
                "SELECT status FROM transfer WHERE withdrawal_account_id = 101 AND deposit_account_id = 202",
                String.class);
    }

    private int ledgerRowCount() {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM ledger_entry WHERE account_id IN (101, 202)", Integer.class);
    }

    private long balance(long accountId) {
        return jdbcTemplate.queryForObject("SELECT balance FROM account WHERE account_id = ?", Long.class, accountId);
    }

    private int publicationCount(ProcessResultStatus status) {
        return jdbcTemplate.queryForObject(
                """
            SELECT COUNT(*) FROM EVENT_PUBLICATION
            WHERE EVENT_TYPE = ? AND SERIALIZED_EVENT LIKE ? AND SERIALIZED_EVENT LIKE ?
            """,
                Integer.class,
                TransferSettled.class.getName(),
                "%\"customerId\":1,%",
                "%\"status\":\"" + status.name() + "\"%");
    }
}
