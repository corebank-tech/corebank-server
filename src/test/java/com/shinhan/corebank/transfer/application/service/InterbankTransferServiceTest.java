package com.shinhan.corebank.transfer.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.shinhan.corebank.IntegrationTestSupport;
import com.shinhan.corebank.account.api.AccountPasswordAuthTokenVerifier;
import com.shinhan.corebank.business.api.BusinessDateGate;
import com.shinhan.corebank.common.domain.ProcessResultStatus;
import com.shinhan.corebank.common.event.DomainEventSink;
import com.shinhan.corebank.common.exception.BusinessException;
import com.shinhan.corebank.gl.api.JournalPostingUseCase;
import com.shinhan.corebank.otp.api.OtpAuthTokenVerifier;
import com.shinhan.corebank.transfer.adapter.out.external.InterbankSocketClient;
import com.shinhan.corebank.transfer.adapter.out.persistence.TransferTestFixtures;
import com.shinhan.corebank.transfer.api.TransferPreCheck;
import com.shinhan.corebank.transfer.application.port.in.InterbankTransferCommand;
import com.shinhan.corebank.transfer.application.port.in.InterbankTransferResult;
import com.shinhan.corebank.transfer.application.port.out.AccountLockPort;
import com.shinhan.corebank.transfer.application.port.out.InterbankBankPort;
import com.shinhan.corebank.transfer.application.port.out.InterbankGatewayPort;
import com.shinhan.corebank.transfer.application.port.out.InterbankOutcome;
import com.shinhan.corebank.transfer.application.port.out.InterbankReply;
import com.shinhan.corebank.transfer.application.port.out.InterbankRequest;
import com.shinhan.corebank.transfer.application.port.out.InterbankTransferPort;
import com.shinhan.corebank.transfer.application.port.out.LedgerSavePort;
import com.shinhan.corebank.transfer.application.port.out.TransferAuthTokenVerificationPort;
import com.shinhan.corebank.transfer.application.port.out.TransferLimitPort;
import com.shinhan.corebank.transfer.application.port.out.TransferOtpVerificationPort;
import com.shinhan.corebank.transfer.application.port.out.TransferSavePort;
import com.shinhan.corebank.transfer.application.port.out.TransferSequencePort;
import com.shinhan.corebank.transfer.config.InterbankProperties;
import com.shinhan.corebank.transfer.domain.exception.TransferErrorCode;
import com.shinhan.mockexternal.MockExternalServer;
import jakarta.persistence.EntityManager;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

// 실제 DB·GL·모의 대외기관으로 끝까지 돌린다. 서비스는 직접 조립한다 — 설정을 바꾸면 컨텍스트가 하나 더 뜬다.
class InterbankTransferServiceTest extends IntegrationTestSupport {

    private static final long WITHDRAWAL_ACCOUNT_ID = 101L;
    private static final long INITIAL_BALANCE = 100_000L;
    private static final long AMOUNT = 30_000L;

    // 당행 이체 통합 테스트와 같은 경계 대역 — 같은 스프링 컨텍스트를 재사용한다.
    @MockitoBean
    private OtpAuthTokenVerifier otpAuthTokenVerifier;

    @MockitoBean
    private AccountPasswordAuthTokenVerifier accountPasswordAuthTokenVerifier;

    @MockitoBean
    private DomainEventSink domainEventSink;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private AccountLockPort accountLockPort;

    @Autowired
    private InterbankBankPort interbankBankPort;

    @Autowired
    private InterbankSettlementService interbankSettlementService;

    @Autowired
    private InterbankTransferPort interbankTransferPort;

    @Autowired
    private TransferLimitPort transferLimitPort;

    @Autowired
    private TransferAuthTokenVerificationPort transferAuthTokenVerificationPort;

    @Autowired
    private TransferOtpVerificationPort transferOtpVerificationPort;

    @Autowired
    private TransferSequencePort transferSequencePort;

    @Autowired
    private TransferSavePort transferSavePort;

    @Autowired
    private LedgerSavePort ledgerSavePort;

    @Autowired
    private JournalPostingUseCase journalPostingUseCase;

    @Autowired
    private List<TransferPreCheck> transferPreChecks;

    @Autowired
    private BusinessDateGate businessDateGate;

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    @Autowired
    private Clock clock;

    private final HttpClient http = HttpClient.newHttpClient();
    private MockExternalServer server;
    private InterbankSocketClient client;

    @BeforeEach
    void setUp() throws IOException {
        server = MockExternalServer.start("127.0.0.1", 0, 0);
        client = new InterbankSocketClient(
                new InterbankProperties(
                        "127.0.0.1",
                        server.port(),
                        Duration.ofMillis(500),
                        Duration.ofMillis(500),
                        new InterbankProperties.CircuitBreaker(50, 100, 100, Duration.ofMinutes(1)),
                        new InterbankProperties.Inquiry(Duration.ZERO, 20)),
                clock);
        new TransactionTemplate(transactionManager)
                .executeWithoutResult(status -> TransferTestFixtures.seedCustomerAndAccounts(entityManager));
    }

    @AfterEach
    void tearDown() {
        server.close();
        jdbcTemplate.update(
                """
                DELETE e FROM gl_journal_entry e JOIN gl_voucher v ON v.voucher_no = e.voucher_no
                WHERE v.tx_type IN ('INTERBANK_TRANSFER', 'INTERBANK_SETTLEMENT')
                """);
        jdbcTemplate.update("DELETE FROM gl_voucher WHERE tx_type IN ('INTERBANK_TRANSFER', 'INTERBANK_SETTLEMENT')");
        jdbcTemplate.update("DELETE FROM ledger_entry WHERE account_id = ?", WITHDRAWAL_ACCOUNT_ID);
        jdbcTemplate.update("DELETE FROM transfer WHERE payee_bank_code IS NOT NULL");
        jdbcTemplate.update(
                "UPDATE account SET balance = ?, status = 'ACTIVE' WHERE account_id = ?",
                INITIAL_BALANCE,
                WITHDRAWAL_ACCOUNT_ID);
        jdbcTemplate.update("DELETE FROM transfer_limit_daily_usage WHERE customer_id = 1");
    }

    private InterbankTransferService service(InterbankGatewayPort gateway) {
        return new InterbankTransferService(
                accountLockPort,
                interbankBankPort,
                gateway,
                interbankSettlementService,
                transferLimitPort,
                transferAuthTokenVerificationPort,
                transferOtpVerificationPort,
                transferSequencePort,
                transferSavePort,
                ledgerSavePort,
                journalPostingUseCase,
                transferPreChecks,
                businessDateGate,
                eventPublisher,
                clock,
                transactionManager);
    }

    private InterbankInquiryService inquiryService(Duration minAge) {
        return new InterbankInquiryService(
                interbankTransferPort,
                client,
                interbankSettlementService,
                new InterbankProperties(
                        "127.0.0.1",
                        server.port(),
                        Duration.ofMillis(500),
                        Duration.ofMillis(500),
                        new InterbankProperties.CircuitBreaker(50, 100, 100, Duration.ofMinutes(1)),
                        new InterbankProperties.Inquiry(minAge, 20)),
                clock);
    }

    private String status(String transactionNumber) {
        return jdbcTemplate.queryForObject(
                "SELECT status FROM transfer WHERE transaction_number = ?", String.class, transactionNumber);
    }

    private static InterbankTransferCommand command(String bankCode, String accountNumber, long amount) {
        return InterbankTransferCommand.builder()
                .customerId(1L)
                .withdrawalAccountId(WITHDRAWAL_ACCOUNT_ID)
                .payeeBankCode(bankCode)
                .depositAccountNumber(accountNumber)
                .amount(amount)
                .myPassbookMemo("생활비")
                .recipientPassbookMemo("홍길동")
                .authToken("account-password-token")
                .otpAuthToken("otp-token")
                .build();
    }

    private long balance() {
        return jdbcTemplate.queryForObject(
                "SELECT balance FROM account WHERE account_id = ?", Long.class, WITHDRAWAL_ACCOUNT_ID);
    }

    // 미결제타점권(10200)의 이 거래 순잔액. 확정되면 0이어야 한다.
    private long unsettledBalance(String transactionNumber) {
        return jdbcTemplate.queryForObject(
                """
                SELECT COALESCE(SUM(CASE WHEN e.dr_cr = 'DEBIT' THEN e.amount ELSE -e.amount END), 0)
                FROM gl_journal_entry e JOIN gl_voucher v ON v.voucher_no = e.voucher_no
                WHERE v.reference_key = ? AND e.account_code = '10200'
                """,
                Long.class,
                transactionNumber);
    }

    private List<Map<String, Object>> vouchers(String transactionNumber) {
        return jdbcTemplate.queryForList(
                """
                SELECT v.tx_type, e.account_code, e.dr_cr FROM gl_voucher v
                JOIN gl_journal_entry e ON e.voucher_no = v.voucher_no
                WHERE v.reference_key = ? ORDER BY v.tx_type DESC, e.line_no
                """,
                transactionNumber);
    }

    private void setFault(String mode) throws IOException, InterruptedException {
        http.send(
                HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + server.adminPort() + "/faults?mode=" + mode))
                        .POST(HttpRequest.BodyPublishers.noBody())
                        .build(),
                HttpResponse.BodyHandlers.discarding());
    }

    @Test
    @DisplayName("0000이면 출금된 채 SUCCESS로 확정되고, 미결제타점권은 송신 때 쌓였다가 결제로 0이 된다")
    void approved_settlesSuccess_andClearsUnsettled() {
        InterbankTransferResult result = service(client).execute(command("004", "1234567890123", AMOUNT));

        assertThat(result.status()).isEqualTo(ProcessResultStatus.SUCCESS);
        assertThat(result.payeeName()).isEqualTo("김민수");
        assertThat(result.payeeBankCode()).isEqualTo("004");
        assertThat(result.withdrawalBalanceAfter()).isEqualTo(INITIAL_BALANCE - AMOUNT);
        assertThat(result.transferredAt()).isNotNull();
        assertThat(balance()).isEqualTo(INITIAL_BALANCE - AMOUNT);
        assertThat(vouchers(result.transactionNumber()))
                .extracting(row -> row.get("tx_type") + " " + row.get("account_code") + " " + row.get("dr_cr"))
                .containsExactly(
                        "INTERBANK_TRANSFER 20100 DEBIT",
                        "INTERBANK_TRANSFER 10200 CREDIT",
                        "INTERBANK_SETTLEMENT 10200 DEBIT",
                        "INTERBANK_SETTLEMENT 10100 CREDIT");
        assertThat(unsettledBalance(result.transactionNumber())).isZero();
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT payee_name FROM transfer WHERE transaction_number = ?",
                        String.class,
                        result.transactionNumber()))
                .isEqualTo("김민수");
    }

    @Test
    @DisplayName("1001이면 출금을 되돌린 뒤 ERROR(TRF0201)로 확정하고, 미결제타점권은 예수금으로 돌아가 0이 된다")
    void rejected_refundsAndSettlesError() {
        InterbankTransferResult result = service(client).execute(command("004", "1001000000000", AMOUNT));

        assertThat(result.status()).isEqualTo(ProcessResultStatus.ERROR);
        assertThat(result.errorCode()).isEqualTo("TRF0201");
        assertThat(result.transactionNumber()).isNotNull();
        assertThat(result.payeeName()).isNull();
        assertThat(balance()).isEqualTo(INITIAL_BALANCE);
        assertThat(vouchers(result.transactionNumber()))
                .extracting(row -> row.get("tx_type") + " " + row.get("account_code") + " " + row.get("dr_cr"))
                .containsExactly(
                        "INTERBANK_TRANSFER 20100 DEBIT",
                        "INTERBANK_TRANSFER 10200 CREDIT",
                        "INTERBANK_SETTLEMENT 10200 DEBIT",
                        "INTERBANK_SETTLEMENT 20100 CREDIT");
        assertThat(unsettledBalance(result.transactionNumber())).isZero();
        List<Map<String, Object>> ledger = jdbcTemplate.queryForList(
                "SELECT direction, reversed, reversal_id FROM ledger_entry WHERE transaction_number = ? ORDER BY direction",
                result.transactionNumber());
        assertThat(ledger)
                .extracting(row ->
                        row.get("direction") + " " + row.get("reversed") + " " + (row.get("reversal_id") != null))
                .containsExactly("DEPOSIT false true", "WITHDRAWAL true false");
    }

    @Test
    @DisplayName("무응답이면 출금된 채 TIMEOUT으로 남고, 조회거래가 0000을 받으면 SUCCESS로 확정한다")
    void noResponse_staysTimeout_thenInquirySettles() throws Exception {
        setFault("NO_RESPONSE");

        InterbankTransferResult result = service(client).execute(command("004", "1234567890123", AMOUNT));

        assertThat(result.status()).isEqualTo(ProcessResultStatus.TIMEOUT);
        assertThat(result.errorCode()).isNull();
        assertThat(balance()).isEqualTo(INITIAL_BALANCE - AMOUNT);
        assertThat(unsettledBalance(result.transactionNumber())).isEqualTo(-AMOUNT);

        setFault("NONE");
        InterbankReply inquired =
                client.inquire(new InterbankRequest("004", result.transactionNumber(), "1234567890123", AMOUNT, "홍길동"));
        interbankSettlementService.settle(result.transactionNumber(), inquired);

        assertThat(jdbcTemplate.queryForObject(
                        "SELECT status FROM transfer WHERE transaction_number = ?",
                        String.class,
                        result.transactionNumber()))
                .isEqualTo("SUCCESS");
        assertThat(balance()).isEqualTo(INITIAL_BALANCE - AMOUNT);
        assertThat(unsettledBalance(result.transactionNumber())).isZero();
    }

    @Test
    @DisplayName("이미 확정된 건에 다른 결과가 다시 와도 바꾸지 않는다 — 출금을 두 번 되돌리지 않는다")
    void settledTransfer_isNotSettledAgain() {
        InterbankTransferResult result = service(client).execute(command("004", "1001000000000", AMOUNT));

        boolean settledAgain = interbankSettlementService
                .settle(result.transactionNumber(), InterbankReply.of(InterbankOutcome.NOT_PROCESSED, "3001"))
                .isPresent();

        assertThat(settledAgain).isFalse();
        assertThat(balance()).isEqualTo(INITIAL_BALANCE);
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT error_code FROM transfer WHERE transaction_number = ?",
                        String.class,
                        result.transactionNumber()))
                .isEqualTo("TRF0201");
    }

    @Test
    @DisplayName("상대 은행 장애로 서킷이 열려 있으면 출금하지 않고 바로 TRF0308로 거부한다")
    void unavailableGateway_rejectsBeforeDebit() {
        InterbankGatewayPort unavailable = mock(InterbankGatewayPort.class);
        when(unavailable.isAvailable()).thenReturn(false);

        InterbankTransferResult result = service(unavailable).execute(command("004", "1234567890123", AMOUNT));

        assertThat(result.status()).isEqualTo(ProcessResultStatus.ERROR);
        assertThat(result.errorCode()).isEqualTo("TRF0308");
        assertThat(result.transactionNumber()).isNull();
        assertThat(balance()).isEqualTo(INITIAL_BALANCE);
    }

    @Test
    @DisplayName("목록에 없는 은행코드면 요청 단계에서 TRF0006을 던지고 transfer 행을 남기지 않는다")
    void unsupportedBank_rejected() {
        assertThatThrownBy(() -> service(client).execute(command("088", "1234567890123", AMOUNT)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(TransferErrorCode.UNSUPPORTED_BANK);
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM transfer WHERE payee_bank_code IS NOT NULL", Long.class))
                .isZero();
    }

    @Test
    @DisplayName("잔액이 모자라면 송신하지 않고 출금 없이 ERROR 행을 남긴다")
    void insufficientBalance_failsBeforeSend() {
        InterbankTransferResult result = service(client).execute(command("004", "1234567890123", INITIAL_BALANCE + 1));

        assertThat(result.status()).isEqualTo(ProcessResultStatus.ERROR);
        assertThat(result.transactionNumber()).isNotNull();
        assertThat(balance()).isEqualTo(INITIAL_BALANCE);
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM ledger_entry WHERE transaction_number = ?",
                        Long.class,
                        result.transactionNumber()))
                .isZero();
    }

    @Test
    @DisplayName("조회거래: 응답 대기보다 오래된 처리 불명 건을 물어 0000이면 SUCCESS로 확정한다")
    void inquiry_settlesTimedOutTransfer() throws Exception {
        setFault("NO_RESPONSE");
        InterbankTransferResult result = service(client).execute(command("004", "1234567890123", AMOUNT));
        setFault("NONE");

        int settled = inquiryService(Duration.ZERO).settleUnsettled();

        assertThat(settled).isEqualTo(1);
        assertThat(status(result.transactionNumber())).isEqualTo("SUCCESS");
        assertThat(unsettledBalance(result.transactionNumber())).isZero();
    }

    @Test
    @DisplayName("조회거래: 조회에도 응답이 없으면 TIMEOUT 그대로 두고, 만든 지 얼마 안 된 건은 묻지 않는다")
    void inquiry_leavesUnknownAndYoungTransfers() throws Exception {
        setFault("NO_RESPONSE");
        InterbankTransferResult result = service(client).execute(command("004", "1234567890123", AMOUNT));

        int whileNoResponse = inquiryService(Duration.ZERO).settleUnsettled();
        setFault("NONE");
        int tooYoung = inquiryService(Duration.ofHours(1)).settleUnsettled();

        assertThat(whileNoResponse).isZero();
        assertThat(tooYoung).isZero();
        assertThat(status(result.transactionNumber())).isEqualTo("TIMEOUT");
        assertThat(balance()).isEqualTo(INITIAL_BALANCE - AMOUNT);
    }

    @Test
    @DisplayName("출금 뒤 전문을 보내기 전에 죽었다면, 조회거래가 3001(원거래 미수신)을 받아 출금을 되돌리고 ERROR로 확정한다")
    void inquiry_refundsWhenOriginalNeverArrived() {
        // 송신 직전 서버가 죽은 상황 — 전문은 나가지 않았고 1단계가 커밋한 TIMEOUT만 남는다.
        InterbankGatewayPort crashedBeforeSend = mock(InterbankGatewayPort.class);
        when(crashedBeforeSend.isAvailable()).thenReturn(true);
        when(crashedBeforeSend.send(any())).thenReturn(InterbankReply.of(InterbankOutcome.UNKNOWN, null));
        InterbankTransferResult result = service(crashedBeforeSend).execute(command("004", "1234567890123", AMOUNT));
        assertThat(result.status()).isEqualTo(ProcessResultStatus.TIMEOUT);

        int settled = inquiryService(Duration.ZERO).settleUnsettled();

        assertThat(settled).isEqualTo(1);
        assertThat(status(result.transactionNumber())).isEqualTo("ERROR");
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT error_code FROM transfer WHERE transaction_number = ?",
                        String.class,
                        result.transactionNumber()))
                .isEqualTo("TRF0308");
        assertThat(balance()).isEqualTo(INITIAL_BALANCE);
        assertThat(unsettledBalance(result.transactionNumber())).isZero();
    }
}
