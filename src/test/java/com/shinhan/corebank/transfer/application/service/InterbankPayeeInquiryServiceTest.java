package com.shinhan.corebank.transfer.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.shinhan.corebank.common.exception.BusinessException;
import com.shinhan.corebank.common.exception.CommonErrorCode;
import com.shinhan.corebank.common.exception.ErrorCode;
import com.shinhan.corebank.transfer.application.port.in.InterbankPayeeInquiryResult;
import com.shinhan.corebank.transfer.application.port.out.InterbankBankPort;
import com.shinhan.corebank.transfer.application.port.out.InterbankGatewayPort;
import com.shinhan.corebank.transfer.application.port.out.InterbankOutcome;
import com.shinhan.corebank.transfer.application.port.out.InterbankReply;
import com.shinhan.corebank.transfer.domain.exception.TransferErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("타행 수취인 사전 조회 서비스")
class InterbankPayeeInquiryServiceTest {

    private static final String BANK = "004";
    private static final String ACCOUNT = "1234567890123";

    @Mock
    InterbankBankPort interbankBankPort;

    @Mock
    InterbankGatewayPort interbankGatewayPort;

    @InjectMocks
    InterbankPayeeInquiryService service;

    @Test
    @DisplayName("상대 은행이 0000으로 답하면 돌려준 예금주를 그대로 낸다 — 마스킹은 응답 DTO가 한다")
    void approved_returnsPayeeName() {
        when(interbankBankPort.isSupported(BANK)).thenReturn(true);
        when(interbankGatewayPort.inquirePayee(BANK, ACCOUNT))
                .thenReturn(new InterbankReply(InterbankOutcome.APPROVED, "김민수", "0000"));

        InterbankPayeeInquiryResult result = service.inquire(BANK, ACCOUNT);

        assertThat(result.payeeName()).isEqualTo("김민수");
    }

    @Test
    @DisplayName("0000이어도 예금주 칸이 비어 있으면 이름을 확인하지 못한 것이라 TRF0309다")
    void approvedWithoutName_unverified() {
        when(interbankBankPort.isSupported(BANK)).thenReturn(true);
        when(interbankGatewayPort.inquirePayee(BANK, ACCOUNT))
                .thenReturn(new InterbankReply(InterbankOutcome.APPROVED, null, "0000"));

        assertErrorCode(() -> service.inquire(BANK, ACCOUNT), TransferErrorCode.INTERBANK_PAYEE_UNVERIFIED);
    }

    @ParameterizedTest(name = "{0} → {1}")
    @CsvSource({
        "ACCOUNT_NOT_FOUND, PAYEE_NOT_FOUND",
        "ACCOUNT_UNAVAILABLE, PAYEE_ACCOUNT_SUSPENDED",
        "NOT_PROCESSED, INTERBANK_PAYEE_UNVERIFIED",
        "UNKNOWN, INTERBANK_PAYEE_UNVERIFIED"
    })
    @DisplayName("계좌 없음·거래 불가는 당행 조회와 같은 코드로, 무응답·상대 장애·서킷 열림은 TRF0309 하나로 거절한다")
    void rejected_mapsToErrorCode(InterbankOutcome outcome, TransferErrorCode expected) {
        when(interbankBankPort.isSupported(BANK)).thenReturn(true);
        when(interbankGatewayPort.inquirePayee(BANK, ACCOUNT)).thenReturn(InterbankReply.of(outcome, null));

        assertErrorCode(() -> service.inquire(BANK, ACCOUNT), expected);
    }

    @Test
    @DisplayName("목록에 없는 은행이면 TRF0006으로 거절하고 전문을 보내지 않는다")
    void unsupportedBank_rejectedWithoutSending() {
        when(interbankBankPort.isSupported("999")).thenReturn(false);

        assertErrorCode(() -> service.inquire("999", ACCOUNT), TransferErrorCode.UNSUPPORTED_BANK);
        verify(interbankGatewayPort, never()).inquirePayee(any(), any());
    }

    @Test
    @DisplayName("계좌번호가 숫자 1~16자리가 아니면 TRF0007로 거절하고 전문을 보내지 않는다")
    void invalidAccountNumber_rejectedWithoutSending() {
        assertErrorCode(
                () -> service.inquire(BANK, "12345678901234567"), TransferErrorCode.INVALID_INTERBANK_ACCOUNT_NUMBER);
        assertErrorCode(() -> service.inquire(BANK, "123-456"), TransferErrorCode.INVALID_INTERBANK_ACCOUNT_NUMBER);
        verify(interbankGatewayPort, never()).inquirePayee(any(), any());
    }

    @Test
    @DisplayName("은행코드나 계좌번호가 없으면 필수값 누락이다")
    void missingInput_rejected() {
        assertErrorCode(() -> service.inquire(null, ACCOUNT), CommonErrorCode.REQUIRED_FIELD_MISSING);
        assertErrorCode(() -> service.inquire(BANK, null), CommonErrorCode.REQUIRED_FIELD_MISSING);
        verify(interbankGatewayPort, never()).inquirePayee(any(), any());
    }

    private static void assertErrorCode(Runnable call, ErrorCode expected) {
        assertThatThrownBy(call::run).isInstanceOf(BusinessException.class).satisfies(e -> assertThat(
                        ((BusinessException) e).getErrorCode())
                .isEqualTo(expected));
    }
}
