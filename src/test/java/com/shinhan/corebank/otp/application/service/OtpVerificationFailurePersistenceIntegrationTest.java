package com.shinhan.corebank.otp.application.service;

import com.shinhan.corebank.IntegrationTestSupport;
import com.shinhan.corebank.account.support.CustomerTestFixture;
import com.shinhan.corebank.otp.api.OtpTransactionType;
import com.shinhan.corebank.otp.application.port.in.IssueOtpCommand;
import com.shinhan.corebank.otp.application.port.in.IssueOtpResult;
import com.shinhan.corebank.otp.application.port.in.IssueOtpUseCase;
import com.shinhan.corebank.otp.application.port.in.VerifyOtpCommand;
import com.shinhan.corebank.otp.application.port.in.VerifyOtpUseCase;
import com.shinhan.corebank.otp.domain.exception.OtpVerificationFailedException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// OTP 오답 예외 반환 후에도 MySQL 오류 횟수와 잠금 상태가 커밋되는지 검증한다.
class OtpVerificationFailurePersistenceIntegrationTest extends IntegrationTestSupport {

    @Autowired CustomerTestFixture customerFixture;
    @Autowired IssueOtpUseCase issueOtpUseCase;
    @Autowired VerifyOtpUseCase verifyOtpUseCase;
    @Autowired JdbcTemplate jdbcTemplate;

    private Long customerId;
    private String otpRequestId;

    @AfterEach
    void cleanUp() {
        if (otpRequestId != null) {
            jdbcTemplate.update(
                    "DELETE FROM verification_request WHERE verification_request_id = ?",
                    otpRequestId
            );
        }
        if (customerId != null) {
            customerFixture.deleteCustomer(customerId);
        }
    }

    @Test
    @DisplayName("다섯 번 오답이면 각 예외 이후에도 error_count 5와 locked true가 저장된다")
    void commitsFailureCountBeforeThrowingApiException() {
        customerId = customerFixture.createCustomer();
        IssueOtpResult issued = issueOtpUseCase.issue(new IssueOtpCommand(
                customerId,
                OtpTransactionType.IMMEDIATE_TRANSFER,
                Map.of("amount", 100_000L)
        ));
        otpRequestId = issued.otpRequestId();
        String wrongCode = issued.otpCode().equals("000000") ? "999999" : "000000";

        for (int attempt = 1; attempt <= 5; attempt++) {
            int expectedAttempt = attempt;
            assertThatThrownBy(() -> verifyOtpUseCase.verify(
                    new VerifyOtpCommand(customerId, otpRequestId, wrongCode)
            )).isInstanceOfSatisfying(OtpVerificationFailedException.class, exception -> {
                assertThat(exception.getAttemptResult().errorCount()).isEqualTo(expectedAttempt);
                assertThat(exception.getErrorCode().getCode())
                        .isEqualTo(expectedAttempt == 5 ? "OTP0103" : "OTP0001");
            });
        }

        Map<String, Object> state = jdbcTemplate.queryForMap(
                """
                SELECT error_count, locked
                FROM verification_request
                WHERE verification_request_id = ?
                """,
                otpRequestId
        );
        assertThat(((Number) state.get("error_count")).intValue()).isEqualTo(5);
        assertThat(state.get("locked")).isEqualTo(true);
    }
}
