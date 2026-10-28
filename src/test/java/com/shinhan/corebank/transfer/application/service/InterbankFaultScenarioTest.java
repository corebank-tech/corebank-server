package com.shinhan.corebank.transfer.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.shinhan.corebank.common.domain.ProcessResultStatus;
import com.shinhan.corebank.transfer.application.port.in.InterbankTransferResult;
import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

// PH-36 장애 주입(#711). 장애마다 송신·조회를 끝까지 돌리고, 중간과 끝에서 자금 정합성을 본다.
class InterbankFaultScenarioTest extends InterbankIntegrationTestSupport {

    // 응답 대기는 500ms다(InterbankIntegrationTestSupport). 그보다 넉넉히 길게·짧게 늦춘다.
    private static final long LONGER_THAN_WAIT_MS = 1_000;
    private static final long SHORTER_THAN_WAIT_MS = 100;
    private static final String APPROVED_ACCOUNT = "1234567890123";

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
}
