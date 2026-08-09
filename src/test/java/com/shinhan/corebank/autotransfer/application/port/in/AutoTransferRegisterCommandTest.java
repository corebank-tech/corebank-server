package com.shinhan.corebank.autotransfer.application.port.in;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.shinhan.corebank.common.exception.BusinessException;
import com.shinhan.corebank.common.exception.CommonErrorCode;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AutoTransferRegisterCommandTest {

    private AutoTransferRegisterCommand.AutoTransferRegisterCommandBuilder validBuilder() {
        return AutoTransferRegisterCommand.builder()
                .customerId(1L)
                .withdrawalAccountId(2L)
                .depositAccountNumber("110987654321")
                .payeeName("홍길동")
                .amount(10000L)
                .cycleMonths(1)
                .transferDay(15)
                .startDate(LocalDate.now().plusDays(1))
                .endDate(LocalDate.now().plusYears(1))
                .myPassbookMemo("내메모")
                .recipientPassbookMemo("받는메모")
                .authToken("token");
    }

    @Test
    @DisplayName("모든 값이 유효하면 정상 생성된다")
    void success() {
        AutoTransferRegisterCommand command = validBuilder().build();

        assertThat(command.depositAccountNumber()).isEqualTo("110987654321");
    }

    @Test
    @DisplayName("필수값이 비어있으면 CMN0002를 던진다")
    void missingRequiredField_throwsRequiredFieldMissing() {
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
    @DisplayName("계좌번호가 12자리 숫자가 아니면 CMN0001을 던진다")
    void invalidAccountNumberFormat_throwsInvalidInput() {
        assertThatThrownBy(() -> validBuilder().depositAccountNumber("abc").build())
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(CommonErrorCode.INVALID_INPUT));
    }

    @Test
    @DisplayName("계좌번호가 11자리면 CMN0001을 던진다")
    void shortAccountNumber_throwsInvalidInput() {
        assertThatThrownBy(() -> validBuilder().depositAccountNumber("1109876543").build())
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(CommonErrorCode.INVALID_INPUT));
    }

    @Test
    @DisplayName("myPassbookMemo/recipientPassbookMemo는 없어도 정상 생성된다")
    void optionalMemosCanBeNull() {
        AutoTransferRegisterCommand command = validBuilder()
                .myPassbookMemo(null)
                .recipientPassbookMemo(null)
                .build();

        assertThat(command.myPassbookMemo()).isNull();
    }
}