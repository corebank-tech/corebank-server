package com.shinhan.corebank.paymentstop.application.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.shinhan.corebank.common.exception.BusinessException;
import com.shinhan.corebank.common.exception.ErrorCode;
import com.shinhan.corebank.paymentstop.application.port.in.CustomerObjectionUseCase.SubmitCommand;
import com.shinhan.corebank.paymentstop.application.port.out.CustomerAccountPort;
import com.shinhan.corebank.paymentstop.application.port.out.ObjectionReviewPort;
import com.shinhan.corebank.paymentstop.application.port.out.PaymentStopRecordPort;
import com.shinhan.corebank.paymentstop.domain.document.UploadFile;
import com.shinhan.corebank.paymentstop.domain.exception.PaymentStopErrorCode;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 고객 이의제기에서 남의 것과 없는 것을 거부할 때 같은 조회만 하는지 본다(AGENTS 규칙 4). 포트 메서드 하나가 쿼리 하나라서 포트 호출이 같으면
 * 연산량도 같다.
 */
@DisplayName("고객 이의제기 본인 확인의 연산량")
class CustomerObjectionServiceTest {

    private static final long CUSTOMER = 7L;
    private static final long OWN_ACCOUNT = 100L;
    private static final long OTHER_ACCOUNT = 200L;
    private static final long MISSING = 1L;
    private static final long OTHERS = 2L;

    private final PaymentStopRecordPort recordPort = mock(PaymentStopRecordPort.class);
    private final CustomerAccountPort customerAccountPort = mock(CustomerAccountPort.class);
    private final CustomerObjectionService service = new CustomerObjectionService(
            recordPort,
            mock(ObjectionReviewPort.class),
            customerAccountPort,
            mock(ObjectionService.class),
            mock(DocumentStore.class),
            mock(ReviewJobEnqueuer.class),
            Clock.systemUTC());

    @BeforeEach
    void setUp() {
        when(customerAccountPort.accountNumbersOf(CUSTOMER)).thenReturn(Map.of(OWN_ACCOUNT, "110000000100"));
    }

    @Test
    @DisplayName("이의제기 신청: 없는 사건과 남의 사건은 같은 조회 두 번만 하고 PST0201로 거부한다")
    void submitRejectsMissingAndOthersCaseWithSameLookups() {
        when(recordPort.findCaseAccountId(MISSING)).thenReturn(Optional.empty());
        when(recordPort.findCaseAccountId(OTHERS)).thenReturn(Optional.of(OTHER_ACCOUNT));

        for (long caseId : new long[] {MISSING, OTHERS}) {
            assertErrorCode(() -> service.submit(CUSTOMER, command(caseId)), PaymentStopErrorCode.CASE_NOT_FOUND);
            verify(customerAccountPort).accountNumbersOf(CUSTOMER);
            verify(recordPort).findCaseAccountId(caseId);
            verifyNoMoreInteractions(customerAccountPort, recordPort);
            clearInvocations(customerAccountPort, recordPort);
        }
    }

    @Test
    @DisplayName("보완 제출: 없는 이의제기와 남의 이의제기는 같은 조회 두 번만 하고 PST0202로 거부한다")
    void supplementRejectsMissingAndOthersObjectionWithSameLookups() {
        when(recordPort.findObjectionAccountId(MISSING)).thenReturn(Optional.empty());
        when(recordPort.findObjectionAccountId(OTHERS)).thenReturn(Optional.of(OTHER_ACCOUNT));

        for (long objectionId : new long[] {MISSING, OTHERS}) {
            assertErrorCode(
                    () -> service.submitSupplement(CUSTOMER, objectionId, List.of(), null),
                    PaymentStopErrorCode.OBJECTION_NOT_FOUND);
            verify(customerAccountPort).accountNumbersOf(CUSTOMER);
            verify(recordPort).findObjectionAccountId(objectionId);
            verifyNoMoreInteractions(customerAccountPort, recordPort);
            clearInvocations(customerAccountPort, recordPort);
        }
    }

    private static SubmitCommand command(long caseId) {
        return new SubmitCommand(
                caseId, 1, "판매 대금으로 받은 돈입니다.", false, false, new UploadFile("신분증.jpg", new byte[] {1}), List.of());
    }

    private static void assertErrorCode(ThrowingCallable call, ErrorCode expected) {
        assertThatThrownBy(call)
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(expected);
    }
}
