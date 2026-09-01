package com.shinhan.corebank.otp.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.shinhan.corebank.otp.api.OtpTransactionType;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

// OTP 요청의 오류 횟수·잠금·사용 완료 상태 전이를 검증한다.
class OtpVerificationRequestTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 20, 10, 0);

    @Test
    @DisplayName("다섯 번째 OTP 오답에서 요청을 잠그고 잔여 횟수를 0으로 만든다")
    void locksOnFifthFailure() {
        OtpVerificationRequest request = request();

        OtpAttemptResult result = null;
        for (int attempt = 0; attempt < 5; attempt++) {
            result = request.recordFailure();
        }

        assertThat(result).isEqualTo(new OtpAttemptResult(5, 0, true));
        assertThat(request.locked()).isTrue();
    }

    @Test
    @DisplayName("OTP 검증 성공 시 사용 완료와 검증 시각을 함께 기록한다")
    void marksVerifiedRequestUsed() {
        OtpVerificationRequest request = request();

        request.verify(NOW.plusSeconds(10));

        assertThat(request.used()).isTrue();
        assertThat(request.verifiedAt()).isEqualTo(NOW.plusSeconds(10));
    }

    @Test
    @DisplayName("오류 횟수와 잠금 상태가 일치하지 않으면 생성할 수 없다")
    void rejectsInconsistentLockState() {
        assertThatThrownBy(() -> new OtpVerificationRequest(
                        "OTP_REQ_test",
                        1L,
                        OtpTransactionType.IMMEDIATE_TRANSFER,
                        "{}",
                        "hash",
                        4,
                        true,
                        false,
                        null,
                        NOW.plusMinutes(3),
                        NOW))
                .isInstanceOf(IllegalStateException.class);
    }

    private OtpVerificationRequest request() {
        return OtpVerificationRequest.issue(
                "OTP_REQ_test",
                1L,
                OtpTransactionType.IMMEDIATE_TRANSFER,
                "{\"amount\":100000}",
                "hash",
                NOW.plusMinutes(3),
                NOW);
    }
}
