package com.shinhan.corebank.account.application.port.in;

import com.shinhan.corebank.account.domain.AccountType;
import com.shinhan.corebank.common.exception.BusinessException;
import com.shinhan.corebank.common.exception.CommonErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProductAccountOpeningCommandTest {

    private static final String PASSWORD_HASH =
            "$2a$10$34abEWY4uXLwTEnT5hNow.603a5rWofFx7Bnj59agU.PsESK0v/Yq";

    @Test
    @DisplayName("상품 계좌 개설 필수값이 누락되면 예외가 발생한다")
    void rejectMissingRequiredField() {
        assertThatThrownBy(
                () -> new ProductAccountOpeningCommand(
                        1L,
                        null,
                        AccountType.TIME_DEPOSIT,
                        PASSWORD_HASH,
                        LocalDate.of(2027, 8, 11)
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
}