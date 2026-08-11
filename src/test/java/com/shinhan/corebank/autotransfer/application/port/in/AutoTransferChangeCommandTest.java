package com.shinhan.corebank.autotransfer.application.port.in;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.shinhan.corebank.autotransfer.domain.AutoTransferErrorCode;
import com.shinhan.corebank.common.exception.BusinessException;
import com.shinhan.corebank.common.exception.CommonErrorCode;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AutoTransferChangeCommandTest {

    private AutoTransferChangeCommand.AutoTransferChangeCommandBuilder validBuilder() {
        return AutoTransferChangeCommand.builder()
                .customerId(1L)
                .amount(20000L)
                .cycleMonths(3)
                .endDate(LocalDate.now().plusYears(2))
                .myPassbookMemo("새메모")
                .recipientPassbookMemo("새받는메모")
                .accountPasswordAuthToken("token")
                .requestIp("127.0.0.1");
    }

    @Test
    @DisplayName("모든 값이 유효하면 정상 생성된다")
    void success() {
        AutoTransferChangeCommand command = validBuilder().build();

        assertThat(command.amount()).isEqualTo(20000L);
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
    @DisplayName("authToken이 공백 문자열이면 CMN0002를 던진다")
    void blankAuthToken_throwsRequiredFieldMissing() {
        assertThatThrownBy(() -> validBuilder().accountPasswordAuthToken("   ").build())
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
        assertThatThrownBy(() -> validBuilder().recipientPassbookMemo("12345678901").build())
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(AutoTransferErrorCode.MEMO_LENGTH_EXCEEDED));
    }

    @Test
    @DisplayName("myPassbookMemo/recipientPassbookMemo는 없어도 정상 생성된다")
    void optionalMemosCanBeNull() {
        AutoTransferChangeCommand command = validBuilder()
                .myPassbookMemo(null)
                .recipientPassbookMemo(null)
                .build();

        assertThat(command.myPassbookMemo()).isNull();
    }

    @Test
    @DisplayName("amount/cycleMonths/endDate가 없어도 정상 생성된다(부분 수정 지원, null=미변경)")
    void amountCycleMonthsEndDateCanBeNull() {
        AutoTransferChangeCommand command = AutoTransferChangeCommand.builder()
                .customerId(1L)
                .accountPasswordAuthToken("token")
                .requestIp("127.0.0.1")
                .build();

        assertThat(command.amount()).isNull();
        assertThat(command.cycleMonths()).isNull();
        assertThat(command.endDate()).isNull();
    }

    @Test
    @DisplayName("withdrawalAccountId를 보내면 AUT0003을 던진다")
    void withdrawalAccountIdPresent_throwsUnmodifiableField() {
        assertThatThrownBy(() -> validBuilder().withdrawalAccountId(2L).build())
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(AutoTransferErrorCode.UNMODIFIABLE_FIELD));
    }

    @Test
    @DisplayName("depositAccountNumber를 보내면 AUT0003을 던진다")
    void depositAccountNumberPresent_throwsUnmodifiableField() {
        assertThatThrownBy(() -> validBuilder().depositAccountNumber("110987654321").build())
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(AutoTransferErrorCode.UNMODIFIABLE_FIELD));
    }

    @Test
    @DisplayName("transferDay를 보내면 AUT0003을 던진다")
    void transferDayPresent_throwsUnmodifiableField() {
        assertThatThrownBy(() -> validBuilder().transferDay(15).build())
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(AutoTransferErrorCode.UNMODIFIABLE_FIELD));
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
        assertThatThrownBy(() -> validBuilder().amount(-500L).build())
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(AutoTransferErrorCode.INVALID_AMOUNT));
    }
}
