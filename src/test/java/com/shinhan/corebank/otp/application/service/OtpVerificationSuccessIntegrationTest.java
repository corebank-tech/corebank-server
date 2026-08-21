package com.shinhan.corebank.otp.application.service;

import com.shinhan.corebank.IntegrationTestSupport;
import com.shinhan.corebank.account.support.CustomerTestFixture;
import com.shinhan.corebank.common.exception.BusinessException;
import com.shinhan.corebank.otp.api.OtpAuthTokenVerification;
import com.shinhan.corebank.otp.api.OtpAuthTokenVerifier;
import com.shinhan.corebank.otp.api.OtpTransactionType;
import com.shinhan.corebank.otp.application.port.in.IssueOtpCommand;
import com.shinhan.corebank.otp.application.port.in.IssueOtpResult;
import com.shinhan.corebank.otp.application.port.in.IssueOtpUseCase;
import com.shinhan.corebank.otp.application.port.in.VerifyOtpCommand;
import com.shinhan.corebank.otp.application.port.in.VerifyOtpResult;
import com.shinhan.corebank.otp.application.port.in.VerifyOtpUseCase;
import com.shinhan.corebank.otp.adapter.out.redis.OtpAuthTokenRedisAdapter;
import com.shinhan.corebank.otp.domain.model.OtpAuthTokenPayload;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// OTP 성공 시 DB 완료 상태와 Redis 300초 토큰 및 최종 거래 일회성을 함께 검증한다.
class OtpVerificationSuccessIntegrationTest extends IntegrationTestSupport {

    private static final String REDIS_PREFIX = "otp:auth:";

    @Autowired CustomerTestFixture customerFixture;
    @Autowired IssueOtpUseCase issueOtpUseCase;
    @Autowired VerifyOtpUseCase verifyOtpUseCase;
    @Autowired OtpAuthTokenVerifier otpAuthTokenVerifier;
    @Autowired OtpAuthTokenRedisAdapter otpAuthTokenRedisAdapter;
    @Autowired StringRedisTemplate redisTemplate;
    @Autowired JdbcTemplate jdbcTemplate;

    private Long customerId;
    private String otpRequestId;
    private String otpAuthToken;

    @AfterEach
    void cleanUp() {
        if (otpAuthToken != null) {
            redisTemplate.delete(REDIS_PREFIX + otpAuthToken);
        }
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
    @DisplayName("정상 OTP 검증은 used와 verified_at을 저장하고 300초 토큰을 한 번만 사용한다")
    void verifiesAndConsumesAuthTokenOnce() {
        customerId = customerFixture.createCustomer();
        Map<String, Object> transactionData = Map.of(
                "withdrawalAccountId", 101L,
                "depositAccountNumber", "110660000103",
                "amount", 100_000L
        );
        IssueOtpResult issued = issueOtpUseCase.issue(new IssueOtpCommand(
                customerId,
                OtpTransactionType.IMMEDIATE_TRANSFER,
                transactionData
        ));
        otpRequestId = issued.otpRequestId();

        VerifyOtpResult verified = verifyOtpUseCase.verify(new VerifyOtpCommand(
                customerId,
                otpRequestId,
                issued.otpCode()
        ));
        otpAuthToken = verified.otpAuthToken();

        Map<String, Object> state = jdbcTemplate.queryForMap(
                """
                SELECT used, verified_at
                FROM verification_request
                WHERE verification_request_id = ?
                """,
                otpRequestId
        );
        Long ttlSeconds = redisTemplate.getExpire(
                REDIS_PREFIX + otpAuthToken,
                TimeUnit.SECONDS
        );

        assertThat(state.get("used")).isEqualTo(true);
        assertThat(state.get("verified_at")).isNotNull();
        assertThat(otpAuthTokenRedisAdapter.find(otpAuthToken))
                .contains(new OtpAuthTokenPayload(otpRequestId, customerId));
        assertThat(ttlSeconds).isBetween(295L, 300L);

        OtpAuthTokenVerification verification = new OtpAuthTokenVerification(
                otpAuthToken,
                customerId,
                OtpTransactionType.IMMEDIATE_TRANSFER,
                transactionData
        );
        otpAuthTokenVerifier.verifyAndConsume(verification);

        assertThatThrownBy(() -> otpAuthTokenVerifier.verifyAndConsume(verification))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode().getCode()).isEqualTo("OTP0101")
                );
    }
}
