package com.shinhan.corebank.transfer.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.shinhan.corebank.common.exception.BusinessException;
import com.shinhan.corebank.transfer.application.port.in.PayeeInquiryResult;
import com.shinhan.corebank.transfer.application.port.out.AccountLockPort;
import com.shinhan.corebank.transfer.application.port.out.LockedAccountStatus;
import com.shinhan.corebank.transfer.application.port.out.LockedAccountType;
import com.shinhan.corebank.transfer.application.port.out.ResolvedPayee;
import com.shinhan.corebank.transfer.domain.exception.TransferErrorCode;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PayeeInquiryServiceTest {

    @Mock
    AccountLockPort accountLockPort;

    @InjectMocks
    PayeeInquiryService payeeInquiryService;

    @Test
    @DisplayName("정상 계좌를 조회하면 계좌ID와 예금주명을 반환한다")
    void inquire_returnsAccountIdAndPayeeName_whenAccountActive() {
        when(accountLockPort.resolvePayeeByAccountNumber("110222222222"))
                .thenReturn(Optional.of(
                        new ResolvedPayee(202L, "테스터", LockedAccountType.DEMAND_DEPOSIT, LockedAccountStatus.ACTIVE)));

        PayeeInquiryResult result = payeeInquiryService.inquire("110222222222");

        assertThat(result)
                .isEqualTo(PayeeInquiryResult.builder()
                        .accountId(202L)
                        .payeeName("테스터")
                        .build());
    }

    @Test
    @DisplayName("존재하지 않는 계좌번호는 TRF0201로 거부된다")
    void inquire_throwsPayeeNotFound_whenAccountDoesNotExist() {
        when(accountLockPort.resolvePayeeByAccountNumber("999999999999")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> payeeInquiryService.inquire("999999999999"))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(TransferErrorCode.PAYEE_NOT_FOUND));
    }

    @Test
    @DisplayName("정지 상태인 계좌는 TRF0301로 거부된다")
    void inquire_throwsPayeeAccountSuspended_whenAccountSuspended() {
        when(accountLockPort.resolvePayeeByAccountNumber("110222222222"))
                .thenReturn(Optional.of(new ResolvedPayee(
                        202L, "테스터", LockedAccountType.DEMAND_DEPOSIT, LockedAccountStatus.SUSPENDED)));

        assertThatThrownBy(() -> payeeInquiryService.inquire("110222222222"))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(TransferErrorCode.PAYEE_ACCOUNT_SUSPENDED));
    }

    @Test
    @DisplayName("해지 상태인 계좌는 TRF0301로 거부된다")
    void inquire_throwsPayeeAccountSuspended_whenAccountClosed() {
        when(accountLockPort.resolvePayeeByAccountNumber("110222222222"))
                .thenReturn(Optional.of(
                        new ResolvedPayee(202L, "테스터", LockedAccountType.DEMAND_DEPOSIT, LockedAccountStatus.CLOSED)));

        assertThatThrownBy(() -> payeeInquiryService.inquire("110222222222"))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(TransferErrorCode.PAYEE_ACCOUNT_SUSPENDED));
    }
}
