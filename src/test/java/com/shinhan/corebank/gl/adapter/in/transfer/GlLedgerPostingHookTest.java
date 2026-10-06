package com.shinhan.corebank.gl.adapter.in.transfer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.groups.Tuple.tuple;

import com.shinhan.corebank.IntegrationTestSupport;
import com.shinhan.corebank.account.api.AccountPasswordAuthTokenVerifier;
import com.shinhan.corebank.common.domain.ProcessResultStatus;
import com.shinhan.corebank.common.event.DomainEventSink;
import com.shinhan.corebank.common.exception.BusinessException;
import com.shinhan.corebank.gl.domain.exception.GlErrorCode;
import com.shinhan.corebank.otp.api.OtpAuthTokenVerifier;
import com.shinhan.corebank.transfer.adapter.out.persistence.TransferTestFixtures;
import com.shinhan.corebank.transfer.api.LedgerPostingContext;
import com.shinhan.corebank.transfer.application.port.in.TransferCommand;
import com.shinhan.corebank.transfer.application.port.in.TransferResult;
import com.shinhan.corebank.transfer.application.service.TransferExecutionService;
import com.shinhan.corebank.transfer.domain.TransferChannel;
import com.shinhan.corebank.transfer.domain.TransferType;
import jakarta.persistence.EntityManager;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 훅 B 의 GL 구현(PH-24)이 실제 이체 경로에서 전표 1건을 세우고, GL 이 실패하면 이체 전체가 롤백되는지 확인한다.
 *
 * <p>MockitoBean 구성은 TransferExecutionServiceTest 와 같게 맞춰 컨텍스트 캐시를 공유한다.
 */
@DisplayName("GL 이체 기표 훅(GlLedgerPostingHook) 통합 테스트")
class GlLedgerPostingHookTest extends IntegrationTestSupport {

    @Autowired
    private TransferExecutionService transferExecutionService;

    @Autowired
    private GlLedgerPostingHook hook;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private Clock clock;

    @MockitoBean
    private OtpAuthTokenVerifier otpAuthTokenVerifier;

    @MockitoBean
    private AccountPasswordAuthTokenVerifier accountPasswordAuthTokenVerifier;

    @MockitoBean
    private DomainEventSink domainEventSink;

    @BeforeEach
    void seed() {
        cleanUpCommittedData();
        new TransactionTemplate(transactionManager)
                .executeWithoutResult(status -> TransferTestFixtures.seedCustomerAndAccounts(entityManager));
    }

    @AfterEach
    void cleanUpCommittedData() {
        jdbcTemplate.update("DELETE e FROM gl_journal_entry e JOIN gl_voucher v ON v.voucher_no = e.voucher_no"
                + " JOIN transfer t ON t.transaction_number = v.reference_key"
                + " WHERE t.withdrawal_account_id = 101 AND t.deposit_account_id = 202");
        jdbcTemplate.update("DELETE v FROM gl_voucher v JOIN transfer t ON t.transaction_number = v.reference_key"
                + " WHERE t.withdrawal_account_id = 101 AND t.deposit_account_id = 202");
        jdbcTemplate.update("DELETE FROM ledger_entry WHERE account_id IN (101, 202)");
        jdbcTemplate.update("DELETE FROM transfer WHERE withdrawal_account_id = 101 AND deposit_account_id = 202");
        jdbcTemplate.update("UPDATE account SET balance = 100000, status = 'ACTIVE' WHERE account_id IN (101, 202)");
        jdbcTemplate.update("DELETE FROM transfer_limit_daily_usage WHERE customer_id = 1");
    }

    @Test
    @DisplayName("즉시이체 1건에 TRF 전표 1건이 거래번호를 참조 키로, 차 예수금 / 대 예수금으로 서고 차대변이 같다")
    void immediateTransferPostsOneTransferVoucher() {
        TransferResult result = transferExecutionService.execute(immediateCommand(30_000L));

        assertThat(result.status()).isEqualTo(ProcessResultStatus.SUCCESS);
        Map<String, Object> voucher = jdbcTemplate.queryForMap(
                "SELECT voucher_no, tx_type, trade_date FROM gl_voucher WHERE reference_key = ?",
                result.transactionNumber());
        assertThat(voucher.get("tx_type")).isEqualTo("TRANSFER");
        assertThat((String) voucher.get("voucher_no")).matches("^[0-9]{8}-TRF-[0-9]{6}$");
        assertThat(jdbcTemplate.queryForList(
                        "SELECT account_code, dr_cr, amount FROM gl_journal_entry WHERE voucher_no = ? ORDER BY line_no",
                        voucher.get("voucher_no")))
                .extracting(row -> row.get("account_code"), row -> row.get("dr_cr"), row -> row.get("amount"))
                .containsExactly(tuple("20100", "DEBIT", 30_000L), tuple("20100", "CREDIT", 30_000L));
    }

    @Test
    @DisplayName("GL 기표가 실패하면 원장·잔액이 롤백되고 이체는 ERROR 로 확정된다")
    void glFailureRollsBackTransfer() {
        // 오늘 TRF 일련번호를 소진시켜 훅 안에서 GLA9005 가 나게 한다.
        LocalDate today = LocalDate.now(clock);
        jdbcTemplate.update(
                "INSERT INTO gl_voucher_sequence (trade_date, tx_type, last_seq, updated_at)"
                        + " VALUES (?, 'TRANSFER', 999999, NOW(6))"
                        + " ON DUPLICATE KEY UPDATE last_seq = 999999",
                today);
        try {
            TransferResult result = transferExecutionService.execute(immediateCommand(30_000L));

            assertThat(result.status()).isEqualTo(ProcessResultStatus.ERROR);
            assertThat(jdbcTemplate.queryForObject(
                            "SELECT COUNT(*) FROM ledger_entry WHERE account_id IN (101, 202)", Integer.class))
                    .isZero();
            assertThat(jdbcTemplate.queryForObject("SELECT balance FROM account WHERE account_id = 101", Long.class))
                    .isEqualTo(100_000L);
            assertThat(jdbcTemplate.queryForObject(
                            "SELECT COUNT(*) FROM gl_voucher WHERE reference_key = ?",
                            Integer.class,
                            result.transactionNumber()))
                    .isZero();
        } finally {
            jdbcTemplate.update("DELETE FROM gl_voucher_sequence WHERE trade_date = ? AND tx_type = 'TRANSFER'", today);
        }
    }

    @Test
    @DisplayName("패턴이 없는 원장 거래유형이 오면 GLA9009 를 던진다")
    void rejectsUnsupportedLedgerTransactionType() {
        LedgerPostingContext unknown = new LedgerPostingContext(
                "20990204WB0000000001", "INTEREST", 1_000L, 101L, 202L, LocalDate.of(2099, 2, 4));

        assertThatThrownBy(() -> hook.afterLedger(unknown))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(GlErrorCode.UNSUPPORTED_LEDGER_TX_TYPE);
    }

    private static TransferCommand immediateCommand(long amount) {
        return TransferCommand.builder()
                .customerId(1L)
                .authToken("dummy-auth-token")
                .otpAuthToken("dummy-otp-token")
                .withdrawalAccountId(101L)
                .depositAccountNumber("110222222222")
                .amount(amount)
                .transferType(TransferType.IMMEDIATE)
                .channel(TransferChannel.WB)
                .build();
    }
}
