package com.shinhan.corebank.autotransfer.application.port.in;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
                .authToken("token")
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

    @Test
    @DisplayName("myPassbookMemo/recipientPassbookMemo는 없어도 정상 생성된다")
    void optionalMemosCanBeNull() {
        AutoTransferChangeCommand command = validBuilder()
                .myPassbookMemo(null)
                .recipientPassbookMemo(null)
                .build();

        assertThat(command.myPassbookMemo()).isNull();
    }
}
