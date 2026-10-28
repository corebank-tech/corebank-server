package com.shinhan.corebank.transfer.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.shinhan.corebank.common.domain.ProcessResultStatus;
import com.shinhan.corebank.transfer.application.port.in.InterbankTransferResult;
import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
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

    private long settlementVouchers(String transactionNumber) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM gl_voucher WHERE reference_key = ? AND tx_type = 'INTERBANK_SETTLEMENT'",
                Long.class,
                transactionNumber);
    }
}
