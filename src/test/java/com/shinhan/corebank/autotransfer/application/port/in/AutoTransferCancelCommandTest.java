package com.shinhan.corebank.autotransfer.application.port.in;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.shinhan.corebank.common.exception.BusinessException;
import com.shinhan.corebank.common.exception.CommonErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AutoTransferCancelCommandTest {

    private AutoTransferCancelCommand.AutoTransferCancelCommandBuilder validBuilder() {
        return AutoTransferCancelCommand.builder()
                .customerId(1L)
                .authToken("token")
                .requestIp("127.0.0.1");
    }

    @Test
    @DisplayName("모든 값이 유효하면 정상 생성된다")
    void success() {
        AutoTransferCancelCommand command = validBuilder().build();

        assertThat(command.customerId()).isEqualTo(1L);
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
        assertThatThrownBy(() -> validBuilder().authToken(null).build())
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
}
