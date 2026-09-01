package com.shinhan.corebank.autotransfer.application.port.in;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.shinhan.corebank.autotransfer.domain.AutoTransferErrorCode;
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
                .accountPasswordAuthToken("token")
                .otpAuthToken("otp-token")
                .requestIp("127.0.0.1");
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
    @DisplayName("payeeName이 공백 문자열이면 CMN0002를 던진다")
    void blankPayeeName_throwsRequiredFieldMissing() {
        assertThatThrownBy(() -> validBuilder().payeeName("   ").build())
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

    @Test
    @DisplayName("계좌번호가 12자리 숫자가 아니면 CMN0001을 던진다")
    void invalidAccountNumberFormat_throwsInvalidInput() {
        assertThatThrownBy(() -> validBuilder().depositAccountNumber("abc").build())
                .isInstanceOf(BusinessException.class)
                .satisfies(e ->
                        assertThat(((BusinessException) e).getErrorCode()).isEqualTo(CommonErrorCode.INVALID_INPUT));
    }

    @Test
    @DisplayName("계좌번호가 11자리면 CMN0001을 던진다")
    void shortAccountNumber_throwsInvalidInput() {
        assertThatThrownBy(
                        () -> validBuilder().depositAccountNumber("1109876543").build())
                .isInstanceOf(BusinessException.class)
                .satisfies(e ->
                        assertThat(((BusinessException) e).getErrorCode()).isEqualTo(CommonErrorCode.INVALID_INPUT));
    }

    @Test
    @DisplayName("amount가 0이면 AUT0008을 던진다")
    void zeroAmount_throwsInvalidAmount() {
        assertThatThrownBy(() -> validBuilder().amount(0L).build())
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(AutoTransferErrorCode.INVALID_AMOUNT));
    }

    @Test
    @DisplayName("amount가 음수면 AUT0008을 던진다")
    void negativeAmount_throwsInvalidAmount() {
        assertThatThrownBy(() -> validBuilder().amount(-1000L).build())
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(AutoTransferErrorCode.INVALID_AMOUNT));
    }

    @Test
    @DisplayName("myPassbookMemo가 10자를 초과하면 AUT0009를 던진다")
    void myPassbookMemoTooLong_throwsMemoLengthExceeded() {
        assertThatThrownBy(() -> validBuilder().myPassbookMemo("12345678901").build())
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(AutoTransferErrorCode.MEMO_LENGTH_EXCEEDED));
    }

    @Test
    @DisplayName("recipientPassbookMemo가 10자를 초과하면 AUT0009를 던진다")
    void recipientPassbookMemoTooLong_throwsMemoLengthExceeded() {
        assertThatThrownBy(() ->
                        validBuilder().recipientPassbookMemo("12345678901").build())
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(AutoTransferErrorCode.MEMO_LENGTH_EXCEEDED));
    }

    @Test
    @DisplayName("myPassbookMemo/recipientPassbookMemo는 없어도 정상 생성된다")
    void optionalMemosCanBeNull() {
        AutoTransferRegisterCommand command =
                validBuilder().myPassbookMemo(null).recipientPassbookMemo(null).build();

        assertThat(command.myPassbookMemo()).isNull();
    }
}
