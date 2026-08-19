package com.shinhan.corebank.account.application.port.in;

import com.shinhan.corebank.common.exception.BusinessException;
import com.shinhan.corebank.common.exception.CommonErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DemandDepositAccountOpeningCommandTest {

    private static final String PASSWORD_HASH =
            "$2a$10$34abEWY4uXLwTEnT5hNow.603a5rWofFx7Bnj59agU.PsESK0v/Yq";

    @Test
    @DisplayName("입출금계좌 개설 고객 ID가 누락되면 예외가 발생한다")
    void rejectMissingCustomerId() {
        assertThatThrownBy(
                () -> new DemandDepositAccountOpeningCommand(
                        null,
                        PASSWORD_HASH
                )
        )
                .isInstanceOf(BusinessException.class)
                .satisfies(throwable -> {
                    BusinessException exception =
                            (BusinessException) throwable;

                    assertThat(exception.getErrorCode())
                            .isEqualTo(
                                    CommonErrorCode.REQUIRED_FIELD_MISSING
                            );
                });
    }

    @Test
    @DisplayName("입출금계좌 개설 고객 ID가 유효하지 않으면 예외가 발생한다")
    void rejectInvalidCustomerId() {
        assertThatThrownBy(
                () -> new DemandDepositAccountOpeningCommand(
                        0L,
                        PASSWORD_HASH
                )
        )
                .isInstanceOf(BusinessException.class)
                .satisfies(throwable -> {
                    BusinessException exception =
                            (BusinessException) throwable;

                    assertThat(exception.getErrorCode())
                            .isEqualTo(
                                    CommonErrorCode.INVALID_INPUT
                            );
                });
    }

    @Test
    @DisplayName("입출금계좌 개설 비밀번호 해시가 누락되면 예외가 발생한다")
    void rejectMissingPasswordHash() {
        assertThatThrownBy(
                () -> new DemandDepositAccountOpeningCommand(
                        1L,
                        null
                )
        )
                .isInstanceOf(BusinessException.class)
                .satisfies(throwable -> {
                    BusinessException exception =
                            (BusinessException) throwable;

                    assertThat(exception.getErrorCode())
                            .isEqualTo(
                                    CommonErrorCode.REQUIRED_FIELD_MISSING
                            );
                });
    }

    @Test
    @DisplayName("입출금계좌 개설 비밀번호 해시가 공백이면 예외가 발생한다")
    void rejectBlankPasswordHash() {
        assertThatThrownBy(
                () -> new DemandDepositAccountOpeningCommand(
                        1L,
                        " "
                )
        )
                .isInstanceOf(BusinessException.class)
                .satisfies(throwable -> {
                    BusinessException exception =
                            (BusinessException) throwable;

                    assertThat(exception.getErrorCode())
                            .isEqualTo(
                                    CommonErrorCode.REQUIRED_FIELD_MISSING
                            );
                });
    }

    @Test
    @DisplayName("입출금계좌 개설 고객 ID가 음수이면 예외가 발생한다")
    void rejectNegativeCustomerId() {
        assertThatThrownBy(
                () -> new DemandDepositAccountOpeningCommand(
                        -1L,
                        PASSWORD_HASH
                )
        )
                .isInstanceOf(BusinessException.class)
                .satisfies(throwable -> {
                    BusinessException exception =
                            (BusinessException) throwable;

                    assertThat(exception.getErrorCode())
                            .isEqualTo(
                                    CommonErrorCode.INVALID_INPUT
                            );
                });
    }
}