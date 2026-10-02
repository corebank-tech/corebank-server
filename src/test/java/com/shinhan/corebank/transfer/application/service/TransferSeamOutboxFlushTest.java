package com.shinhan.corebank.transfer.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.shinhan.corebank.IntegrationTestSupport;
import com.shinhan.corebank.account.api.AccountPasswordAuthTokenVerifier;
import com.shinhan.corebank.common.domain.ProcessResultStatus;
import com.shinhan.corebank.otp.api.OtpAuthTokenVerifier;
import com.shinhan.corebank.transfer.adapter.out.persistence.TransferTestFixtures;
import com.shinhan.corebank.transfer.api.LedgerPostingHook;
import com.shinhan.corebank.transfer.application.port.in.TransferCommand;
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
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 이체 확장점(PH-99) 위에 아웃박스(PH-96)가 설 수 있는지 BEFORE_COMMIT 리스너의 트랜잭션 경계를 고정한다.
 *
 * 훅 B 빈이 이벤트를 발행하고(EVT-2 발행 자리의 대역), BEFORE_COMMIT 리스너가 JdbcTemplate으로 INSERT한다(PH-96 리스너의 대역).
 * 아웃박스 테이블이 생기면 프로브 테이블을 그것으로 바꾼다. 빈으로 등록해 실제 자동 주입과 이벤트 인프라를 탄다.
 */
@Import(TransferSeamOutboxFlushTest.ProbeConfig.class)
class TransferSeamOutboxFlushTest extends IntegrationTestSupport {

    @Autowired
    private TransferExecutionService transferExecutionService;

    @Autowired
    private ProbeOutboxListener probeOutboxListener;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @MockitoBean
    private OtpAuthTokenVerifier otpAuthTokenVerifier;

    @MockitoBean
    private AccountPasswordAuthTokenVerifier accountPasswordAuthTokenVerifier;

    @BeforeEach
    void setUp() {
        jdbcTemplate.execute(
                "CREATE TABLE IF NOT EXISTS seam_outbox_probe (transaction_number VARCHAR(30) PRIMARY KEY)");
        cleanUpCommittedData();
        probeOutboxListener.failAfterInsert = false;
        new TransactionTemplate(transactionManager)
                .executeWithoutResult(status -> TransferTestFixtures.seedCustomerAndAccounts(entityManager));
    }

    @AfterEach
    void tearDown() {
        cleanUpCommittedData();
        jdbcTemplate.execute("DROP TABLE IF EXISTS seam_outbox_probe");
    }

    @Test
    @DisplayName("BEFORE_COMMIT 리스너의 INSERT는 이체와 같은 커밋에 실린다")
    void listenerInsert_commitsWithTransfer() {
        TransferResult result = transferExecutionService.execute(immediateCommand());

        assertThat(result.status()).isEqualTo(ProcessResultStatus.SUCCESS);
        assertThat(probeRows()).containsExactly(result.transactionNumber());
    }

    @Test
    @DisplayName("BEFORE_COMMIT 리스너가 예외를 던지면 이체·원장·잔액과 리스너 INSERT가 함께 롤백된다")
    void listenerFailure_rollsBackTransferAndInsert() {
        probeOutboxListener.failAfterInsert = true;

        assertThatThrownBy(() -> transferExecutionService.execute(immediateCommand()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("outbox insert failed");

        assertThat(probeRows()).isEmpty();
        assertThat(balanceOf(101)).isEqualTo(100000L);
        assertThat(balanceOf(202)).isEqualTo(100000L);
        Integer ledgerRows = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM ledger_entry WHERE account_id IN (101, 202)", Integer.class);
        assertThat(ledgerRows).isZero();
        List<String> statuses = jdbcTemplate.queryForList(
                "SELECT status FROM transfer WHERE withdrawal_account_id = 101 AND deposit_account_id = 202",
                String.class);
        assertThat(statuses).containsExactly("ERROR");
    }

    private void cleanUpCommittedData() {
        jdbcTemplate.update("DELETE FROM seam_outbox_probe");
        jdbcTemplate.update("DELETE FROM ledger_entry WHERE account_id IN (101, 202)");
        jdbcTemplate.update("DELETE FROM transfer WHERE withdrawal_account_id = 101 AND deposit_account_id = 202");
        jdbcTemplate.update("UPDATE account SET balance = 100000, status = 'ACTIVE' WHERE account_id IN (101, 202)");
        jdbcTemplate.update("DELETE FROM transfer_limit_daily_usage WHERE customer_id = 1");
    }

    private List<String> probeRows() {
        return jdbcTemplate.queryForList("SELECT transaction_number FROM seam_outbox_probe", String.class);
    }

    private long balanceOf(long accountId) {
        return jdbcTemplate.queryForObject("SELECT balance FROM account WHERE account_id = ?", Long.class, accountId);
    }

    private static TransferCommand immediateCommand() {
        return TransferCommand.builder()
                .customerId(1L)
                .authToken("dummy-auth-token")
                .otpAuthToken("dummy-otp-token")
                .withdrawalAccountId(101L)
                .depositAccountNumber("110222222222")
                .amount(30000L)
                .transferType(TransferType.IMMEDIATE)
                .channel(TransferChannel.WB)
                .build();
    }

    record ProbeTransferPosted(String transactionNumber) {}

    static class ProbeOutboxListener {

        private final JdbcTemplate jdbcTemplate;
        volatile boolean failAfterInsert;

        ProbeOutboxListener(JdbcTemplate jdbcTemplate) {
            this.jdbcTemplate = jdbcTemplate;
        }

        // INSERT 뒤에 던져야 "리스너가 쓴 행도 함께 롤백된다"까지 검증된다.
        @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
        public void on(ProbeTransferPosted event) {
            jdbcTemplate.update(
                    "INSERT INTO seam_outbox_probe (transaction_number) VALUES (?)", event.transactionNumber());
            if (failAfterInsert) {
                throw new IllegalStateException("outbox insert failed");
            }
        }
    }

    @TestConfiguration
    static class ProbeConfig {

        @Bean
        LedgerPostingHook probeLedgerPostingHook(ApplicationEventPublisher publisher) {
            return context -> publisher.publishEvent(new ProbeTransferPosted(context.transactionNumber()));
        }

        @Bean
        ProbeOutboxListener probeOutboxListener(JdbcTemplate jdbcTemplate) {
            return new ProbeOutboxListener(jdbcTemplate);
        }
    }
}
