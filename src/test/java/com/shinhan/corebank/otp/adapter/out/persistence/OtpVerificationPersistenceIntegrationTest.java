package com.shinhan.corebank.otp.adapter.out.persistence;

import com.shinhan.corebank.IntegrationTestSupport;
import com.shinhan.corebank.account.support.CustomerTestFixture;
import com.shinhan.corebank.otp.api.OtpTransactionType;
import com.shinhan.corebank.otp.application.port.in.IssueOtpCommand;
import com.shinhan.corebank.otp.application.port.in.IssueOtpResult;
import com.shinhan.corebank.otp.application.port.in.IssueOtpUseCase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

// OTP가 기존 verification_request에 저장되고 재발급 시 이전 요청이 만료되는지 검증한다.
@Transactional
class OtpVerificationPersistenceIntegrationTest extends IntegrationTestSupport {

    @Autowired CustomerTestFixture customerFixture;
    @Autowired IssueOtpUseCase issueOtpUseCase;
    @Autowired JdbcTemplate jdbcTemplate;
    @Autowired Clock clock;

    @Test
    @DisplayName("두 번째 OTP 발급은 기존 활성 OTP를 만료시키고 신규 요청만 활성으로 남긴다")
    void reissueExpiresPreviousRequest() {
        Long customerId = customerFixture.createCustomer();
        Map<String, Object> data = Map.of(
                "withdrawalAccountId", 101L,
                "depositAccountNumber", "110660000103",
                "amount", 100_000L
        );

        IssueOtpResult first = issueOtpUseCase.issue(new IssueOtpCommand(
                customerId,
                OtpTransactionType.IMMEDIATE_TRANSFER,
                data
        ));
        IssueOtpResult second = issueOtpUseCase.issue(new IssueOtpCommand(
                customerId,
                OtpTransactionType.IMMEDIATE_TRANSFER,
                data
        ));

        Map<String, Object> firstRow = jdbcTemplate.queryForMap(
                """
                SELECT purpose, target, code_hash, transaction_type,
                       error_count, locked, used, expires_at
                FROM verification_request
                WHERE verification_request_id = ?
                """,
                first.otpRequestId()
        );
        Integer activeCount = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM verification_request
                WHERE customer_id = ?
                  AND purpose = 'OTP_TRANSACTION'
                  AND used = FALSE
                  AND locked = FALSE
                  AND expires_at > ?
                """,
                Integer.class,
                customerId,
                LocalDateTime.now(clock)
        );
        LocalDateTime secondExpiresAt = jdbcTemplate.queryForObject(
                """
                SELECT expires_at
                FROM verification_request
                WHERE verification_request_id = ?
                """,
                LocalDateTime.class,
                second.otpRequestId()
        );

        assertThat(firstRow.get("purpose")).isEqualTo("OTP_TRANSACTION");
        assertThat(firstRow.get("target")).isNull();
        assertThat(firstRow.get("code_hash").toString()).isNotEqualTo(first.otpCode());
        assertThat(firstRow.get("transaction_type")).isEqualTo("IMMEDIATE_TRANSFER");
        assertThat(activeCount).isOne();
        assertThat(secondExpiresAt).isAfter(LocalDateTime.now(clock).plusMinutes(2));
    }
}
