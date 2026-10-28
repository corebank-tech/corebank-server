package com.shinhan.corebank.transfer.application.service;

import com.shinhan.corebank.IntegrationTestSupport;
import com.shinhan.corebank.account.api.AccountPasswordAuthTokenVerifier;
import com.shinhan.corebank.business.api.BusinessDateGate;
import com.shinhan.corebank.common.event.DomainEventSink;
import com.shinhan.corebank.gl.api.JournalPostingUseCase;
import com.shinhan.corebank.otp.api.OtpAuthTokenVerifier;
import com.shinhan.corebank.transfer.adapter.out.external.InterbankSocketClient;
import com.shinhan.corebank.transfer.adapter.out.persistence.TransferTestFixtures;
import com.shinhan.corebank.transfer.api.TransferPreCheck;
import com.shinhan.corebank.transfer.application.port.in.InterbankTransferCommand;
import com.shinhan.corebank.transfer.application.port.out.AccountLockPort;
import com.shinhan.corebank.transfer.application.port.out.InterbankBankPort;
import com.shinhan.corebank.transfer.application.port.out.InterbankGatewayPort;
import com.shinhan.corebank.transfer.application.port.out.InterbankTransferPort;
import com.shinhan.corebank.transfer.application.port.out.LedgerSavePort;
import com.shinhan.corebank.transfer.application.port.out.TransferAuthTokenVerificationPort;
import com.shinhan.corebank.transfer.application.port.out.TransferLimitPort;
import com.shinhan.corebank.transfer.application.port.out.TransferOtpVerificationPort;
import com.shinhan.corebank.transfer.application.port.out.TransferSavePort;
import com.shinhan.corebank.transfer.application.port.out.TransferSequencePort;
import com.shinhan.corebank.transfer.config.InterbankProperties;
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

// 실제 DB·GL·모의 대외기관으로 끝까지 돌린다. 서비스는 직접 조립한다 — 설정을 바꾸면 컨텍스트가 하나 더 뜬다.
abstract class InterbankIntegrationTestSupport extends IntegrationTestSupport {

    static final long WITHDRAWAL_ACCOUNT_ID = 101L;
    static final long INITIAL_BALANCE = 100_000L;
    static final long AMOUNT = 30_000L;

    // 당행 이체 통합 테스트와 같은 경계 대역 — 같은 스프링 컨텍스트를 재사용한다.
    @MockitoBean
    OtpAuthTokenVerifier otpAuthTokenVerifier;

    @MockitoBean
    AccountPasswordAuthTokenVerifier accountPasswordAuthTokenVerifier;

    @MockitoBean
    DomainEventSink domainEventSink;

    @Autowired
    EntityManager entityManager;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    PlatformTransactionManager transactionManager;

    @Autowired
    AccountLockPort accountLockPort;

    @Autowired
    InterbankBankPort interbankBankPort;

    @Autowired
    InterbankSettlementService interbankSettlementService;

    @Autowired
    InterbankTransferPort interbankTransferPort;

    @Autowired
    TransferLimitPort transferLimitPort;

    @Autowired
    TransferAuthTokenVerificationPort transferAuthTokenVerificationPort;

    @Autowired
    TransferOtpVerificationPort transferOtpVerificationPort;

    @Autowired
    TransferSequencePort transferSequencePort;

    @Autowired
    TransferSavePort transferSavePort;

    @Autowired
    LedgerSavePort ledgerSavePort;

    @Autowired
    JournalPostingUseCase journalPostingUseCase;

    @Autowired
    List<TransferPreCheck> transferPreChecks;

    @Autowired
    BusinessDateGate businessDateGate;

    @Autowired
    ApplicationEventPublisher eventPublisher;

    @Autowired
    Clock clock;

    private final HttpClient http = HttpClient.newHttpClient();
    MockExternalServer server;
    InterbankSocketClient client;

    @BeforeEach
    void setUpInterbank() throws IOException {
        server = MockExternalServer.start("127.0.0.1", 0, 0);
        client = new InterbankSocketClient(properties(Duration.ZERO), clock);
        new TransactionTemplate(transactionManager)
                .executeWithoutResult(status -> TransferTestFixtures.seedCustomerAndAccounts(entityManager));
    }

    @AfterEach
    void tearDownInterbank() {
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

    private InterbankProperties properties(Duration inquiryMinAge) {
        return new InterbankProperties(
                "127.0.0.1",
                server.port(),
                Duration.ofMillis(500),
                Duration.ofMillis(500),
                new InterbankProperties.CircuitBreaker(50, 100, 100, Duration.ofMinutes(1)),
                new InterbankProperties.Inquiry(inquiryMinAge, 20));
    }

    InterbankTransferService service(InterbankGatewayPort gateway) {
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

    InterbankInquiryService inquiryService(Duration minAge) {
        return new InterbankInquiryService(
                interbankTransferPort, client, interbankSettlementService, properties(minAge), clock);
    }

    String status(String transactionNumber) {
        return jdbcTemplate.queryForObject(
                "SELECT status FROM transfer WHERE transaction_number = ?", String.class, transactionNumber);
    }

    static InterbankTransferCommand command(String bankCode, String accountNumber, long amount) {
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

    long balance() {
        return jdbcTemplate.queryForObject(
                "SELECT balance FROM account WHERE account_id = ?", Long.class, WITHDRAWAL_ACCOUNT_ID);
    }

    // 미결제타점권(10200)의 이 거래 순잔액. 확정되면 0이어야 한다.
    long unsettledBalance(String transactionNumber) {
        return jdbcTemplate.queryForObject(
                """
                SELECT COALESCE(SUM(CASE WHEN e.dr_cr = 'DEBIT' THEN e.amount ELSE -e.amount END), 0)
                FROM gl_journal_entry e JOIN gl_voucher v ON v.voucher_no = e.voucher_no
                WHERE v.reference_key = ? AND e.account_code = '10200'
                """,
                Long.class,
                transactionNumber);
    }

    List<Map<String, Object>> vouchers(String transactionNumber) {
        return jdbcTemplate.queryForList(
                """
                SELECT v.tx_type, e.account_code, e.dr_cr FROM gl_voucher v
                JOIN gl_journal_entry e ON e.voucher_no = v.voucher_no
                WHERE v.reference_key = ? ORDER BY v.tx_type DESC, e.line_no
                """,
                transactionNumber);
    }

    void setFault(String mode) throws IOException, InterruptedException {
        switchFault("mode=" + mode);
    }

    // 모의 대외기관은 요청을 장부에 남긴 뒤 늦게 답한다 — 상대는 처리했는데 응답만 늦는 상황이다.
    void setDelay(long delayMs) throws IOException, InterruptedException {
        switchFault("mode=DELAY&delayMs=" + delayMs);
    }

    private void switchFault(String query) throws IOException, InterruptedException {
        http.send(
                HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + server.adminPort() + "/faults?" + query))
                        .POST(HttpRequest.BodyPublishers.noBody())
                        .build(),
                HttpResponse.BodyHandlers.discarding());
    }
}
