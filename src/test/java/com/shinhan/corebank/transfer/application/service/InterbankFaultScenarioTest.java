package com.shinhan.corebank.transfer.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.shinhan.corebank.common.domain.ProcessResultStatus;
import com.shinhan.corebank.transfer.application.port.in.InterbankTransferResult;
import com.shinhan.corebank.transfer.application.port.out.InterbankOutcome;
import com.shinhan.corebank.transfer.application.port.out.InterbankReply;
import com.shinhan.corebank.transfer.application.port.out.InterbankRequest;
import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

// PH-36 장애 주입(#711). 장애마다 송신·조회를 끝까지 돌리고, 중간과 끝에서 자금 정합성을 본다.
class InterbankFaultScenarioTest extends InterbankIntegrationTestSupport {

    // 응답 대기는 500ms다(InterbankIntegrationTestSupport). 그보다 넉넉히 길게·짧게 늦춘다.
    private static final long LONGER_THAN_WAIT_MS = 1_000;
    private static final long SHORTER_THAN_WAIT_MS = 100;
    private static final String APPROVED_ACCOUNT = "1234567890123";
    private static final String NOT_FOUND_ACCOUNT = "1001000000000";

    @Test
    @DisplayName("타임아웃: 응답이 대기 시간보다 늦으면 출금된 채 TIMEOUT으로 남고, 장애가 풀린 뒤 조회거래로 SUCCESS 확정된다")
    void delayLongerThanWait_staysTimeout_thenInquirySettles() throws Exception {
        FundIntegrityCheck integrity = FundIntegrityCheck.begin(jdbcTemplate, WITHDRAWAL_ACCOUNT_ID);
        setDelay(LONGER_THAN_WAIT_MS);

        InterbankTransferResult result = service(client).execute(command("004", APPROVED_ACCOUNT, AMOUNT));

        assertThat(result.status()).isEqualTo(ProcessResultStatus.TIMEOUT);
        assertThat(balance()).isEqualTo(INITIAL_BALANCE - AMOUNT);
        integrity.assertHolds();

        setFault("NONE");
        int settled = inquiryService(Duration.ZERO).settleUnsettled();

        assertThat(settled).isEqualTo(1);
        assertThat(status(result.transactionNumber())).isEqualTo("SUCCESS");
        assertThat(unsettledBalance(result.transactionNumber())).isZero();
        integrity.assertHolds();
    }

    @Test
    @DisplayName("타임아웃 경계: 응답이 늦어도 대기 시간 안에 오면 송신 직후 SUCCESS로 확정된다")
    void delayShorterThanWait_settlesOnSend() throws Exception {
        FundIntegrityCheck integrity = FundIntegrityCheck.begin(jdbcTemplate, WITHDRAWAL_ACCOUNT_ID);
        setDelay(SHORTER_THAN_WAIT_MS);

        InterbankTransferResult result = service(client).execute(command("004", APPROVED_ACCOUNT, AMOUNT));

        assertThat(result.status()).isEqualTo(ProcessResultStatus.SUCCESS);
        assertThat(unsettledBalance(result.transactionNumber())).isZero();
        integrity.assertHolds();
    }

    @ParameterizedTest(name = "조회도 {0}")
    @ValueSource(strings = {"DELAY", "NO_RESPONSE"})
    @DisplayName("조회 단계 장애: 조회에도 답을 못 받으면 TIMEOUT 그대로 두고, 장애가 풀린 다음 주기에 SUCCESS로 확정된다")
    void inquiryFault_leavesTimeout_thenNextCycleSettles(String inquiryFault) throws Exception {
        FundIntegrityCheck integrity = FundIntegrityCheck.begin(jdbcTemplate, WITHDRAWAL_ACCOUNT_ID);
        setFault("NO_RESPONSE");
        InterbankTransferResult result = service(client).execute(command("004", APPROVED_ACCOUNT, AMOUNT));
        assertThat(result.status()).isEqualTo(ProcessResultStatus.TIMEOUT);

        if ("DELAY".equals(inquiryFault)) {
            setDelay(LONGER_THAN_WAIT_MS);
        } else {
            setFault(inquiryFault);
        }
        int settledDuringFault = inquiryService(Duration.ZERO).settleUnsettled();

        assertThat(settledDuringFault).isZero();
        assertThat(status(result.transactionNumber())).isEqualTo("TIMEOUT");
        assertThat(balance()).isEqualTo(INITIAL_BALANCE - AMOUNT);
        integrity.assertHolds();

        setFault("NONE");
        int settledAfterRecovery = inquiryService(Duration.ZERO).settleUnsettled();

        assertThat(settledAfterRecovery).isEqualTo(1);
        assertThat(status(result.transactionNumber())).isEqualTo("SUCCESS");
        integrity.assertHolds();
    }

    @Test
    @DisplayName("송신 응답 중복: 같은 응답이 두 번 와도 SUCCESS 확정은 한 번이고 확정 전표도 1건이다")
    void duplicateReplyOnSend_settlesOnce() throws Exception {
        FundIntegrityCheck integrity = FundIntegrityCheck.begin(jdbcTemplate, WITHDRAWAL_ACCOUNT_ID);
        setFault("DUPLICATE");

        InterbankTransferResult result = service(client).execute(command("004", APPROVED_ACCOUNT, AMOUNT));

        assertThat(result.status()).isEqualTo(ProcessResultStatus.SUCCESS);
        assertThat(settlementVouchers(result.transactionNumber())).isEqualTo(1);
        integrity.assertHolds();
    }

    @Test
    @DisplayName("송신 응답 중복 + 실패 확정: 1001이 두 번 와도 출금은 정확히 한 번 되돌린다")
    void duplicateReplyOnSend_refundsOnce() throws Exception {
        FundIntegrityCheck integrity = FundIntegrityCheck.begin(jdbcTemplate, WITHDRAWAL_ACCOUNT_ID);
        setFault("DUPLICATE");

        InterbankTransferResult result = service(client).execute(command("004", NOT_FOUND_ACCOUNT, AMOUNT));

        assertThat(result.status()).isEqualTo(ProcessResultStatus.ERROR);
        assertThat(result.errorCode()).isEqualTo("TRF0201");
        assertThat(balance()).isEqualTo(INITIAL_BALANCE);
        assertThat(settlementVouchers(result.transactionNumber())).isEqualTo(1);
        integrity.assertHolds();
    }

    @Test
    @DisplayName("조회 응답 중복: TIMEOUT 건의 조회 응답이 두 번 와도 한 번만 확정하고, 다시 조회해도 바꾸지 않는다")
    void duplicateReplyOnInquiry_settlesOnce() throws Exception {
        FundIntegrityCheck integrity = FundIntegrityCheck.begin(jdbcTemplate, WITHDRAWAL_ACCOUNT_ID);
        setFault("NO_RESPONSE");
        InterbankTransferResult result = service(client).execute(command("004", APPROVED_ACCOUNT, AMOUNT));
        assertThat(result.status()).isEqualTo(ProcessResultStatus.TIMEOUT);

        setFault("DUPLICATE");
        int settled = inquiryService(Duration.ZERO).settleUnsettled();
        int settledAgain = inquiryService(Duration.ZERO).settleUnsettled();

        assertThat(settled).isEqualTo(1);
        assertThat(settledAgain).isZero();
        assertThat(status(result.transactionNumber())).isEqualTo("SUCCESS");
        assertThat(settlementVouchers(result.transactionNumber())).isEqualTo(1);
        integrity.assertHolds();
    }

    @ParameterizedTest(name = "원거래 계좌 {0} → {1}")
    @CsvSource({"1234567890123, SUCCESS", "1001000000000, ERROR"})
    @DisplayName("중복거래 2001: 같은 일련번호를 다시 보내 2001을 받아도 확정하지 않고, 조회거래가 원거래 결과로 한 번만 확정한다")
    void duplicateTransaction_isNotSettled_untilInquiry(String accountNumber, String expectedStatus) throws Exception {
        FundIntegrityCheck integrity = FundIntegrityCheck.begin(jdbcTemplate, WITHDRAWAL_ACCOUNT_ID);
        setFault("NO_RESPONSE");
        InterbankTransferResult result = service(client).execute(command("004", accountNumber, AMOUNT));
        assertThat(result.status()).isEqualTo(ProcessResultStatus.TIMEOUT);
        setFault("NONE");

        // 재시도 금지라 서비스는 다시 보내지 않는다. 같은 전문이 한 번 더 간 상황을 클라이언트로 직접 만든다.
        InterbankReply resent =
                client.send(new InterbankRequest("004", result.transactionNumber(), accountNumber, AMOUNT, "홍길동"));
        boolean settledByDuplicate = interbankSettlementService
                .settle(result.transactionNumber(), resent)
                .isPresent();

        assertThat(resent.outcome()).isEqualTo(InterbankOutcome.UNKNOWN);
        assertThat(resent.responseCode()).isEqualTo("2001");
        assertThat(settledByDuplicate).isFalse();
        assertThat(status(result.transactionNumber())).isEqualTo("TIMEOUT");
        integrity.assertHolds();

        int settled = inquiryService(Duration.ZERO).settleUnsettled();

        assertThat(settled).isEqualTo(1);
        assertThat(status(result.transactionNumber())).isEqualTo(expectedStatus);
        integrity.assertHolds();
    }

    private long settlementVouchers(String transactionNumber) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM gl_voucher WHERE reference_key = ? AND tx_type = 'INTERBANK_SETTLEMENT'",
                Long.class,
                transactionNumber);
    }
}
