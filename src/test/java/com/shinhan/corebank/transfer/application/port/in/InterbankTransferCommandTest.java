package com.shinhan.corebank.transfer.application.port.in;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.shinhan.corebank.common.exception.BusinessException;
import com.shinhan.corebank.common.exception.CommonErrorCode;
import com.shinhan.corebank.transfer.domain.exception.TransferErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayName("InterbankTransferCommand 요청 검증")
class InterbankTransferCommandTest {

    private static InterbankTransferCommand.InterbankTransferCommandBuilder valid() {
        return InterbankTransferCommand.builder()
                .customerId(1L)
                .withdrawalAccountId(101L)
                .payeeBankCode("004")
                .depositAccountNumber("1234567890123")
                .amount(50000L)
                .myPassbookMemo("생활비")
                .recipientPassbookMemo("홍길동")
                .authToken("auth")
                .otpAuthToken("otp");
    }

    @ParameterizedTest
    @ValueSource(strings = {"1", "110123456877", "1234567890123456"})
    @DisplayName("숫자 1~16자리 계좌번호는 받는다")
    void acceptsOneToSixteenDigits(String accountNumber) {
        assertThatCode(() -> valid().depositAccountNumber(accountNumber).build())
                .doesNotThrowAnyException();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "12345678901234567", "123-456-7890", "abc"})
    @DisplayName("숫자 1~16자리가 아니면 TRF0007")
    void rejectsInvalidAccountNumber(String accountNumber) {
        assertThatThrownBy(() -> valid().depositAccountNumber(accountNumber).build())
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(TransferErrorCode.INVALID_INTERBANK_ACCOUNT_NUMBER);
    }

    @Test
    @DisplayName("받는 분 통장 표시에 MS949로 못 담는 문자(이모지)가 있으면 TRF0008, 똠·햏 같은 확장 한글은 받는다")
    void rejectsUnencodableRecipientMemo() {
        assertThatThrownBy(() -> valid().recipientPassbookMemo("홍길동😀").build())
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(TransferErrorCode.UNSENDABLE_MEMO_CHARACTER);
        assertThatCode(() -> valid().recipientPassbookMemo("똠햏").build()).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("통장 표시 10자 초과는 TRF0005, 금액 0은 TRF0003, 인증 토큰이 없으면 CMN 필수값 누락")
    void rejectsOtherInvalidInputs() {
        assertThatThrownBy(() -> valid().myPassbookMemo("12345678901").build())
                .extracting("errorCode")
                .isEqualTo(TransferErrorCode.MEMO_LENGTH_EXCEEDED);
        assertThatThrownBy(() -> valid().amount(0L).build())
                .extracting("errorCode")
                .isEqualTo(TransferErrorCode.INVALID_AMOUNT);
        assertThatThrownBy(() -> valid().otpAuthToken(" ").build())
                .extracting("errorCode")
                .isEqualTo(CommonErrorCode.REQUIRED_FIELD_MISSING);
    }
}
