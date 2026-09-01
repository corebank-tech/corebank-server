package com.shinhan.corebank.autotransfer.application.port.in;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.shinhan.corebank.common.exception.BusinessException;
import com.shinhan.corebank.common.exception.CommonErrorCode;
import java.util.Arrays;
import java.util.List;
import java.util.stream.LongStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AutoTransferCancelCommandTest {

    private AutoTransferCancelCommand.AutoTransferCancelCommandBuilder validBuilder() {
        return AutoTransferCancelCommand.builder()
                .customerId(1L)
                .autoTransferIds(List.of(20L))
                .accountPasswordAuthToken("token")
                .otpAuthToken("otp-token")
                .requestIp("127.0.0.1");
    }

    @Test
    @DisplayName("모든 값이 유효하면 정상 생성된다")
    void success() {
        AutoTransferCancelCommand command = validBuilder().build();

        assertThat(command.customerId()).isEqualTo(1L);
        assertThat(command.autoTransferIds()).containsExactly(20L);
    }

    @Test
    @DisplayName("해지할 ID 목록이 비어 있으면 CMN0002를 던진다")
    void emptyIds_throwsRequiredFieldMissing() {
        assertThatThrownBy(() -> validBuilder().autoTransferIds(List.of()).build())
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(CommonErrorCode.REQUIRED_FIELD_MISSING));
    }

    @Test
    @DisplayName("ID 목록에 null 원소가 섞이면 CMN0002를 던진다")
    void idsContainingNull_throwsRequiredFieldMissing() {
        assertThatThrownBy(() ->
                        validBuilder().autoTransferIds(Arrays.asList(20L, null)).build())
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(CommonErrorCode.REQUIRED_FIELD_MISSING));
    }

    @Test
    @DisplayName("ID 목록은 오름차순 정렬·중복 제거된다 — OTP 거래정보의 배열 순서를 발급 시점과 맞추기 위한 계약")
    void ids_areSortedAndDeduplicated() {
        AutoTransferCancelCommand command =
                validBuilder().autoTransferIds(List.of(30L, 10L, 20L, 10L)).build();

        assertThat(command.autoTransferIds()).containsExactly(10L, 20L, 30L);
    }

    @Test
    @DisplayName("정확히 50건이면 정상 생성된다 — 경계 바로 아래를 함께 고정해야 한도가 잠긴다")
    void exactlyMaxIds_isAccepted() {
        List<Long> ids = LongStream.rangeClosed(1, AutoTransferCancelCommand.MAX_CANCEL_COUNT)
                .boxed()
                .toList();

        AutoTransferCancelCommand command = validBuilder().autoTransferIds(ids).build();

        assertThat(command.autoTransferIds()).hasSize(AutoTransferCancelCommand.MAX_CANCEL_COUNT);
    }

    @Test
    @DisplayName("중복을 제거한 뒤 50건을 넘으면 CMN0001을 던진다")
    void tooManyIds_throwsInvalidInput() {
        List<Long> ids = LongStream.rangeClosed(1, AutoTransferCancelCommand.MAX_CANCEL_COUNT + 1)
                .boxed()
                .toList();

        assertThatThrownBy(() -> validBuilder().autoTransferIds(ids).build())
                .isInstanceOf(BusinessException.class)
                .satisfies(e ->
                        assertThat(((BusinessException) e).getErrorCode()).isEqualTo(CommonErrorCode.INVALID_INPUT));
    }

    @Test
    @DisplayName("customerId가 없으면 CMN0002를 던진다")
    void missingCustomerId_throwsRequiredFieldMissing() {
        assertThatThrownBy(() -> validBuilder().customerId(null).build())
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(CommonErrorCode.REQUIRED_FIELD_MISSING));
    }

    @Test
    @DisplayName("authToken이 없으면 CMN0002를 던진다")
    void missingAuthToken_throwsRequiredFieldMissing() {
        assertThatThrownBy(() -> validBuilder().accountPasswordAuthToken(null).build())
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(CommonErrorCode.REQUIRED_FIELD_MISSING));
    }

    @Test
    @DisplayName("otpAuthToken이 없으면 CMN0002를 던진다")
    void missingOtpAuthToken_throwsRequiredFieldMissing() {
        assertThatThrownBy(() -> validBuilder().otpAuthToken(null).build())
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(CommonErrorCode.REQUIRED_FIELD_MISSING));
    }

    @Test
    @DisplayName("otpAuthToken이 공백 문자열이면 CMN0002를 던진다")
    void blankOtpAuthToken_throwsRequiredFieldMissing() {
        assertThatThrownBy(() -> validBuilder().otpAuthToken("   ").build())
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(CommonErrorCode.REQUIRED_FIELD_MISSING));
    }

    @Test
    @DisplayName("requestIp가 없으면 CMN0002를 던진다")
    void missingRequestIp_throwsRequiredFieldMissing() {
        assertThatThrownBy(() -> validBuilder().requestIp(null).build())
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(CommonErrorCode.REQUIRED_FIELD_MISSING));
    }

    @Test
    @DisplayName("requestIp가 공백 문자열이면 CMN0002를 던진다")
    void blankRequestIp_throwsRequiredFieldMissing() {
        assertThatThrownBy(() -> validBuilder().requestIp("   ").build())
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(CommonErrorCode.REQUIRED_FIELD_MISSING));
    }

    @Test
    @DisplayName("authToken이 공백 문자열이면 CMN0002를 던진다")
    void blankAuthToken_throwsRequiredFieldMissing() {
        assertThatThrownBy(() -> validBuilder().accountPasswordAuthToken("   ").build())
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(CommonErrorCode.REQUIRED_FIELD_MISSING));
    }
}
