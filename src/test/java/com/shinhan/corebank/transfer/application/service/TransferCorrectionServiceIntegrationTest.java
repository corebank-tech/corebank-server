package com.shinhan.corebank.transfer.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.groups.Tuple.tuple;

import com.shinhan.corebank.IntegrationTestSupport;
import com.shinhan.corebank.account.api.AccountPasswordAuthTokenVerifier;
import com.shinhan.corebank.business.support.BusinessDateTestFixture;
import com.shinhan.corebank.common.domain.ProcessResultStatus;
import com.shinhan.corebank.common.exception.BusinessException;
import com.shinhan.corebank.notification.application.port.in.NotificationCommandUseCase;
import com.shinhan.corebank.otp.api.OtpAuthTokenVerifier;
import com.shinhan.corebank.transfer.application.port.in.TransferCommand;
import com.shinhan.corebank.transfer.application.port.in.TransferCorrectionUseCase.TransferRepostResult;
import com.shinhan.corebank.transfer.application.port.in.TransferCorrectionUseCase.TransferReversalResult;
import com.shinhan.corebank.transfer.application.port.in.TransferResult;
import com.shinhan.corebank.transfer.domain.TransferChannel;
import com.shinhan.corebank.transfer.domain.TransferType;
import com.shinhan.corebank.transfer.domain.exception.TransferErrorCode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * PH-80 정정 체인 취소정정·정상거래 통합 테스트. 원거래는 실제 이체 실행으로 만들고 결과는 커밋된 행을 다시 읽어 본다.
 * 스레드마다 독립 트랜잭션이 필요해 이 클래스는 @Transactional을 두지 않는다.
 */
@DisplayName("TransferCorrectionService — 정정 체인 취소정정·정상거래 (#548)")
class TransferCorrectionServiceIntegrationTest extends IntegrationTestSupport {

    private static final long CUSTOMER_ID = 548L;
    private static final long ACCOUNT_A = 54801L;
    private static final long ACCOUNT_B = 54802L;
    private static final String ACCOUNT_A_NUMBER = "548000000001";
    private static final String ACCOUNT_B_NUMBER = "548000000002";
    private static final long STARTING_BALANCE_A = 100_000L;
    private static final long AMOUNT = 50_000L;
    // GL 전표 채번이 거래일별이라 다른 테스트가 쓰지 않는 영업일을 고른다.
    private static final LocalDate ORIGINAL_DATE = LocalDate.of(2032, 3, 4);
    private static final LocalDate CORRECTION_DATE = LocalDate.of(2032, 3, 5);

    @Autowired
    private TransferCorrectionService transferCorrectionService;

    @Autowired
    private TransferExecutionService transferExecutionService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private BusinessDateTestFixture businessDateTestFixture;

    private LocalDate originalBusinessDate;

    @MockitoBean
    private OtpAuthTokenVerifier otpAuthTokenVerifier;

    @MockitoBean
    private AccountPasswordAuthTokenVerifier accountPasswordAuthTokenVerifier;

    // 이체가 실제 고객으로 알림을 남기면 정리 단계의 고객 삭제가 notification FK 에 막힌다. 알림은 이 테스트의 관심사가 아니다(#706).
    @MockitoBean
    private NotificationCommandUseCase notificationCommandUseCase;

    @BeforeEach
    void setUp() {
        cleanUp();
        seed();
        originalBusinessDate = businessDateTestFixture.currentInDb();
    }

    @AfterEach
    void tearDown() {
        businessDateTestFixture.moveTo(originalBusinessDate);
        cleanUp();
    }

    @Test
    @DisplayName("정정 거래의 거래일은 원거래일이 아니라 정정한 날의 영업일이다 — 원거래 행은 그대로 둔다")
    void correction_tradeDateIsBusinessDateOfCorrection() {
        // given: 영업일 D1에 원거래, 영업일이 D2로 넘어간 뒤 정정
        businessDateTestFixture.moveTo(ORIGINAL_DATE);
        TransferResult original = transfer(ACCOUNT_A, ACCOUNT_B_NUMBER, AMOUNT);
        businessDateTestFixture.moveTo(CORRECTION_DATE);

        // when
        TransferReversalResult reversal = transferCorrectionService.reverse(original.transactionNumber());
        TransferRepostResult repost = transferCorrectionService.repost(original.transactionNumber(), 30_000L);

        // then: 원거래는 D1 그대로
        assertThat(transferTradeDate(original.transactionNumber())).isEqualTo(ORIGINAL_DATE);
        assertThat(ledgerTradeDates(original.transactionNumber())).containsExactly(ORIGINAL_DATE, ORIGINAL_DATE);

        // then: 취소정정·정상거래는 transfer 행·원장 2행·GL 전표가 모두 D2
        for (String correctionNumber :
                List.of(reversal.reversalTransactionNumber(), repost.repostTransactionNumber())) {
            assertThat(transferTradeDate(correctionNumber)).isEqualTo(CORRECTION_DATE);
            assertThat(ledgerTradeDates(correctionNumber)).containsExactly(CORRECTION_DATE, CORRECTION_DATE);
            assertThat(jdbcTemplate.queryForObject(
                            "SELECT trade_date FROM gl_voucher WHERE reference_key = ?",
                            LocalDate.class,
                            correctionNumber))
                    .isEqualTo(CORRECTION_DATE);
        }
    }

    @Test
    @DisplayName("성공한 이체를 되돌리면 잔액이 원복되고, 원거래는 지워지지 않고 무효 표시만 남으며, 체인을 원거래에서 따라갈 수 있다")
    void reverse_restoresBalancesWithoutDeleting() {
        // given: A → B 50,000원
        TransferResult original = transfer(ACCOUNT_A, ACCOUNT_B_NUMBER, AMOUNT);

        // when
        TransferReversalResult result = transferCorrectionService.reverse(original.transactionNumber());

        // then: 잔액이 원래대로
        assertThat(result.amount()).isEqualTo(AMOUNT);
        assertThat(balanceOf(ACCOUNT_A)).isEqualTo(STARTING_BALANCE_A);
        assertThat(balanceOf(ACCOUNT_B)).isZero();

        // then: 삭제 0건 — 이체 2행(원거래·취소정정), 원장 4행(원거래 2·반대기표 2)
        assertThat(countTransfers()).isEqualTo(2);
        assertThat(countLedgerRows()).isEqualTo(4);

        // then: 원거래는 SUCCESS 그대로 무효 시각만 생겼고, 원장 2행은 reversed
        Map<String, Object> originalRow = transferRow(original.transactionNumber());
        assertThat(originalRow.get("status")).isEqualTo(ProcessResultStatus.SUCCESS.name());
        assertThat(originalRow.get("invalidated_at")).isNotNull();
        assertThat(reversedLedgerCount(original.transactionNumber())).isEqualTo(2);

        // then: 원거래 하나에서 취소정정을 찾을 수 있다
        Map<String, Object> reversalRow = jdbcTemplate.queryForMap(
                "SELECT * FROM transfer WHERE ref_transfer_id = ? AND correction_type = 'REVERSAL'",
                originalRow.get("transfer_id"));
        assertThat(reversalRow.get("transaction_number")).isEqualTo(result.reversalTransactionNumber());
        assertThat(reversalRow.get("withdrawal_account_id")).isEqualTo(ACCOUNT_B);
        assertThat(reversalRow.get("deposit_account_id")).isEqualTo(ACCOUNT_A);
        assertThat(reversalRow.get("status")).isEqualTo(ProcessResultStatus.SUCCESS.name());

        // then: 계좌마다 원장 증감 합계 = 잔액 증감 (시드 잔액은 원장 없이 넣었다)
        assertThat(signedLedgerSum(ACCOUNT_A)).isEqualTo(balanceOf(ACCOUNT_A) - STARTING_BALANCE_A);
        assertThat(signedLedgerSum(ACCOUNT_B)).isEqualTo(balanceOf(ACCOUNT_B));
    }

    @Test
    @DisplayName("타행 이체는 받은 계좌가 당행에 없어 되돌릴 수 없다 — TRF0305로 거부하고 원거래는 그대로다 (#654)")
    void reverseInterbank_throws() {
        // given
        jdbcTemplate.update(
                """
                INSERT INTO transfer (transaction_number, withdrawal_account_id, deposit_account_id, deposit_account_number,
                    payee_bank_code, payee_name, amount, fee, transfer_type, channel, status, transferred_at, created_at)
                VALUES ('20320304WB0000054899', ?, NULL, '1234567890123', '004', '김민수', ?, 0, 'IMMEDIATE', 'WB',
                    'SUCCESS', NOW(6), NOW(6))
                """,
                ACCOUNT_A,
                AMOUNT);

        // when & then
        assertThatThrownBy(() -> transferCorrectionService.reverse("20320304WB0000054899"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(TransferErrorCode.NOT_CORRECTABLE);
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT invalidated_at IS NULL FROM transfer WHERE transaction_number = '20320304WB0000054899'",
                        Boolean.class))
                .isTrue();
    }

    @Test
    @DisplayName("이미 되돌린 이체를 다시 되돌리면 TRF0305로 거부되고 아무것도 바뀌지 않는다")
    void reverseTwice_throws() {
        // given
        TransferResult original = transfer(ACCOUNT_A, ACCOUNT_B_NUMBER, AMOUNT);
        transferCorrectionService.reverse(original.transactionNumber());

        // when & then
        assertThatThrownBy(() -> transferCorrectionService.reverse(original.transactionNumber()))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(TransferErrorCode.NOT_CORRECTABLE);
        assertThat(countTransfers()).isEqualTo(2);
        assertThat(balanceOf(ACCOUNT_A)).isEqualTo(STARTING_BALANCE_A);
    }

    @Test
    @DisplayName("받은 사람이 돈을 이미 써서 잔액이 모자라면 TRF0306으로 거부되고 원거래는 무효화되지 않는다")
    void reverse_whenPayeeSpentMoney_throws() {
        // given: A → B 50,000원, 이후 B가 30,000원을 다시 보내 B 잔액 20,000원
        TransferResult original = transfer(ACCOUNT_A, ACCOUNT_B_NUMBER, AMOUNT);
        transfer(ACCOUNT_B, ACCOUNT_A_NUMBER, 30_000L);

        // when & then
        assertThatThrownBy(() -> transferCorrectionService.reverse(original.transactionNumber()))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(TransferErrorCode.REVERSAL_INSUFFICIENT_BALANCE);
        assertThat(transferRow(original.transactionNumber()).get("invalidated_at"))
                .isNull();
        assertThat(reversedLedgerCount(original.transactionNumber())).isZero();
        assertThat(balanceOf(ACCOUNT_B)).isEqualTo(20_000L);
    }

    @Test
    @DisplayName("같은 이체를 동시에 두 번 되돌려도 정확히 한 번만 되돌려진다")
    void concurrentReverse_onlyOneSucceeds() throws Exception {
        // given
        TransferResult original = transfer(ACCOUNT_A, ACCOUNT_B_NUMBER, AMOUNT);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        Callable<TransferReversalResult> reverse = () -> {
            start.await();
            return transferCorrectionService.reverse(original.transactionNumber());
        };

        try {
            List<Future<TransferReversalResult>> futures = List.of(pool.submit(reverse), pool.submit(reverse));

            // when
            start.countDown();
            int succeeded = 0;
            List<Object> rejectedCodes = new ArrayList<>();
            for (Future<TransferReversalResult> future : futures) {
                try {
                    future.get(30, TimeUnit.SECONDS);
                    succeeded++;
                } catch (ExecutionException e) {
                    assertThat(e.getCause()).isInstanceOf(BusinessException.class);
                    rejectedCodes.add(((BusinessException) e.getCause()).getErrorCode());
                }
            }

            // then
            assertThat(succeeded).isEqualTo(1);
            assertThat(rejectedCodes).containsExactly(TransferErrorCode.NOT_CORRECTABLE);
            assertThat(countTransfers()).isEqualTo(2);
            assertThat(balanceOf(ACCOUNT_A)).isEqualTo(STARTING_BALANCE_A);
            assertThat(balanceOf(ACCOUNT_B)).isZero();
        } finally {
            pool.shutdownNow();
            pool.awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    @Test
    @DisplayName("취소정정 뒤 고친 금액으로 정상거래를 하면 잔액이 의도대로 되고, 원거래 하나에서 취소정정·정상거래를 모두 찾는다")
    void repost_afterReverse_completesChain() {
        // given: 30,000원을 보내려다 50,000원을 보냈고, 되돌렸다
        TransferResult original = transfer(ACCOUNT_A, ACCOUNT_B_NUMBER, AMOUNT);
        transferCorrectionService.reverse(original.transactionNumber());

        // when
        TransferRepostResult result = transferCorrectionService.repost(original.transactionNumber(), 30_000L);

        // then: 원래 의도대로 30,000원만 옮겨졌다
        assertThat(result.amount()).isEqualTo(30_000L);
        assertThat(balanceOf(ACCOUNT_A)).isEqualTo(STARTING_BALANCE_A - 30_000L);
        assertThat(balanceOf(ACCOUNT_B)).isEqualTo(30_000L);

        // then: 삭제 0건 — 이체 3행, 원장 6행
        assertThat(countTransfers()).isEqualTo(3);
        assertThat(countLedgerRows()).isEqualTo(6);

        // then: 원거래 하나에서 체인 전체를 찾는다
        Object originalId = transferRow(original.transactionNumber()).get("transfer_id");
        List<Map<String, Object>> chain = jdbcTemplate.queryForList(
                "SELECT correction_type, withdrawal_account_id, amount, status FROM transfer WHERE ref_transfer_id = ?",
                originalId);
        assertThat(chain).extracting(row -> row.get("correction_type")).containsExactlyInAnyOrder("REVERSAL", "REPOST");
        Map<String, Object> repost = chain.stream()
                .filter(row -> "REPOST".equals(row.get("correction_type")))
                .findFirst()
                .orElseThrow();
        assertThat(repost.get("withdrawal_account_id")).isEqualTo(ACCOUNT_A);
        assertThat(repost.get("amount")).isEqualTo(30_000L);
        assertThat(repost.get("status")).isEqualTo(ProcessResultStatus.SUCCESS.name());

        // then: 체인의 거래마다 GL 전표가 1건씩 선다 — 원거래·정상거래는 TRF, 취소정정은 REV (PH-24)
        List<Map<String, Object>> vouchers = jdbcTemplate.queryForList(
                "SELECT t.correction_type, v.tx_type, SUM(e.amount) AS line_total FROM transfer t"
                        + " JOIN gl_voucher v ON v.reference_key = t.transaction_number"
                        + " JOIN gl_journal_entry e ON e.voucher_no = v.voucher_no"
                        + " WHERE t.transfer_id = ? OR t.ref_transfer_id = ?"
                        + " GROUP BY t.correction_type, v.tx_type",
                originalId,
                originalId);
        assertThat(vouchers)
                .extracting(row -> row.get("correction_type"), row -> row.get("tx_type"), row -> ((Number)
                                row.get("line_total"))
                        .longValue())
                .containsExactlyInAnyOrder(
                        tuple(null, "TRANSFER", 2 * AMOUNT),
                        tuple("REVERSAL", "REVERSAL", 2 * AMOUNT),
                        tuple("REPOST", "TRANSFER", 2 * 30_000L));

        // then: 계좌마다 원장 증감 합계 = 잔액 증감
        assertThat(signedLedgerSum(ACCOUNT_A)).isEqualTo(balanceOf(ACCOUNT_A) - STARTING_BALANCE_A);
        assertThat(signedLedgerSum(ACCOUNT_B)).isEqualTo(balanceOf(ACCOUNT_B));
    }

    @Test
    @DisplayName("취소정정하지 않은 원거래에 정상거래를 하면 TRF0305로 거부된다 — 돈이 이중으로 나가지 않는다")
    void repost_withoutReverse_throws() {
        // given
        TransferResult original = transfer(ACCOUNT_A, ACCOUNT_B_NUMBER, AMOUNT);

        // when & then
        assertThatThrownBy(() -> transferCorrectionService.repost(original.transactionNumber(), 30_000L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(TransferErrorCode.NOT_CORRECTABLE);
        assertThat(countTransfers()).isEqualTo(1);
        assertThat(balanceOf(ACCOUNT_B)).isEqualTo(AMOUNT);
    }

    @Test
    @DisplayName("정상거래를 두 번 하면 두 번째는 TRF0305로 거부된다")
    void repostTwice_throws() {
        // given
        TransferResult original = transfer(ACCOUNT_A, ACCOUNT_B_NUMBER, AMOUNT);
        transferCorrectionService.reverse(original.transactionNumber());
        transferCorrectionService.repost(original.transactionNumber(), 30_000L);

        // when & then
        assertThatThrownBy(() -> transferCorrectionService.repost(original.transactionNumber(), 30_000L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(TransferErrorCode.NOT_CORRECTABLE);
        assertThat(countTransfers()).isEqualTo(3);
        assertThat(balanceOf(ACCOUNT_B)).isEqualTo(30_000L);
    }

    private TransferResult transfer(long withdrawalAccountId, String depositAccountNumber, long amount) {
        TransferResult result = transferExecutionService.execute(TransferCommand.builder()
                .customerId(CUSTOMER_ID)
                .authToken("dummy-auth-token")
                .otpAuthToken("dummy-otp-token")
                .withdrawalAccountId(withdrawalAccountId)
                .depositAccountNumber(depositAccountNumber)
                .amount(amount)
                .transferType(TransferType.IMMEDIATE)
                .channel(TransferChannel.WB)
                .myPassbookMemo("548원거래")
                .recipientPassbookMemo("548원거래")
                .build());
        assertThat(result.status()).isEqualTo(ProcessResultStatus.SUCCESS);
        return result;
    }

    private void seed() {
        jdbcTemplate.update(
                """
            INSERT INTO customer (customer_id, user_id, password_hash, user_name, birth_date, email, phone_number, joined_at, created_at, updated_at)
            VALUES (?, 'issue548', '$2a$10$abcdefghijklmnopqrstuvwxyz1234567890abcdefghijklm', '테스터', '1990-01-01', 'issue548@test.com', '01054800000', NOW(6), NOW(6), NOW(6))
            """,
                CUSTOMER_ID);

        // 없으면 이체 경로가 LMT9001로 거부된다(#388).
        jdbcTemplate.update(
                """
            INSERT INTO transfer_limit (customer_id, one_time_limit, daily_limit, created_at, updated_at)
            VALUES (?, 1000000, 5000000, NOW(6), NOW(6))
            """,
                CUSTOMER_ID);

        jdbcTemplate.update(
                """
            INSERT INTO account (account_id, account_number, customer_id, product_id, account_type, balance, status, password_hash, withdrawal_registered, withdrawal_registered_at, opened_date, created_at, updated_at)
            VALUES (?, ?, ?, NULL, 'DEMAND_DEPOSIT', ?, 'ACTIVE', '$2a$10$abcdefghijklmnopqrstuvwxyz1234567890abcdefghijklm', TRUE, NOW(6), '2026-08-01', NOW(6), NOW(6)),
                   (?, ?, ?, NULL, 'DEMAND_DEPOSIT', 0, 'ACTIVE', '$2a$10$abcdefghijklmnopqrstuvwxyz1234567890abcdefghijklm', TRUE, NOW(6), '2026-08-01', NOW(6), NOW(6))
            """,
                ACCOUNT_A,
                ACCOUNT_A_NUMBER,
                CUSTOMER_ID,
                STARTING_BALANCE_A,
                ACCOUNT_B,
                ACCOUNT_B_NUMBER,
                CUSTOMER_ID);
    }

    // 정정 거래가 원거래를 FK로 가리키므로 정정 거래부터 지운다.
    private void cleanUp() {
        jdbcTemplate.update(
                "DELETE e FROM gl_journal_entry e JOIN gl_voucher v ON v.voucher_no = e.voucher_no"
                        + " JOIN transfer t ON t.transaction_number = v.reference_key"
                        + " WHERE t.withdrawal_account_id IN (?, ?)",
                ACCOUNT_A,
                ACCOUNT_B);
        jdbcTemplate.update(
                "DELETE v FROM gl_voucher v JOIN transfer t ON t.transaction_number = v.reference_key"
                        + " WHERE t.withdrawal_account_id IN (?, ?)",
                ACCOUNT_A,
                ACCOUNT_B);
        jdbcTemplate.update("DELETE FROM ledger_entry WHERE account_id IN (?, ?)", ACCOUNT_A, ACCOUNT_B);
        jdbcTemplate.update(
                "DELETE FROM transfer WHERE withdrawal_account_id IN (?, ?) AND ref_transfer_id IS NOT NULL",
                ACCOUNT_A,
                ACCOUNT_B);
        jdbcTemplate.update("DELETE FROM transfer WHERE withdrawal_account_id IN (?, ?)", ACCOUNT_A, ACCOUNT_B);
        jdbcTemplate.update("DELETE FROM transfer_limit_daily_usage WHERE customer_id = ?", CUSTOMER_ID);
        jdbcTemplate.update("DELETE FROM transfer_limit_history WHERE customer_id = ?", CUSTOMER_ID);
        jdbcTemplate.update("DELETE FROM transfer_limit WHERE customer_id = ?", CUSTOMER_ID);
        jdbcTemplate.update("DELETE FROM account WHERE account_id IN (?, ?)", ACCOUNT_A, ACCOUNT_B);
        jdbcTemplate.update("DELETE FROM customer WHERE customer_id = ?", CUSTOMER_ID);
        jdbcTemplate.update(
                "DELETE FROM gl_voucher_sequence WHERE trade_date IN (?, ?)", ORIGINAL_DATE, CORRECTION_DATE);
    }

    private LocalDate transferTradeDate(String transactionNumber) {
        return jdbcTemplate.queryForObject(
                "SELECT trade_date FROM transfer WHERE transaction_number = ?", LocalDate.class, transactionNumber);
    }

    private List<LocalDate> ledgerTradeDates(String transactionNumber) {
        return jdbcTemplate.queryForList(
                "SELECT trade_date FROM ledger_entry WHERE transaction_number = ?", LocalDate.class, transactionNumber);
    }

    private long balanceOf(long accountId) {
        return jdbcTemplate.queryForObject("SELECT balance FROM account WHERE account_id = ?", Long.class, accountId);
    }

    private long countTransfers() {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM transfer WHERE withdrawal_account_id IN (?, ?)",
                Long.class,
                ACCOUNT_A,
                ACCOUNT_B);
    }

    private long countLedgerRows() {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM ledger_entry WHERE account_id IN (?, ?)", Long.class, ACCOUNT_A, ACCOUNT_B);
    }

    private long reversedLedgerCount(String transactionNumber) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM ledger_entry WHERE transaction_number = ? AND reversed = TRUE",
                Long.class,
                transactionNumber);
    }

    private long signedLedgerSum(long accountId) {
        return jdbcTemplate.queryForObject(
                """
                SELECT COALESCE(SUM(CASE direction WHEN 'DEPOSIT' THEN amount ELSE -amount END), 0)
                FROM ledger_entry WHERE account_id = ?
                """,
                Long.class,
                accountId);
    }

    private Map<String, Object> transferRow(String transactionNumber) {
        return jdbcTemplate.queryForMap("SELECT * FROM transfer WHERE transaction_number = ?", transactionNumber);
    }
}
