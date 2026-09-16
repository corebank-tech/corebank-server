package com.shinhan.corebank.signup.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.shinhan.corebank.common.exception.BusinessException;
import com.shinhan.corebank.signup.application.port.out.ExistingBankCustomerAccountsPort;
import com.shinhan.corebank.signup.application.port.out.SignupCustomerAvailabilityPort;
import com.shinhan.corebank.signup.application.port.out.SignupRegisteredAccountPort;
import com.shinhan.corebank.signup.domain.exception.SignupErrorCode;
import com.shinhan.corebank.signup.domain.model.ExistingBankAccountSnapshot;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

// 원장 고객 재가입 판정의 두 경로를 검증한다.
@ExtendWith(MockitoExtension.class)
class RegisteredExistingBankCustomerCheckerTest {

    private static final String BANK_CUSTOMER_ID = "BANK_CUSTOMER_001";

    @Mock
    SignupCustomerAvailabilityPort availabilityPort;

    @Mock
    ExistingBankCustomerAccountsPort accountsPort;

    @Mock
    SignupRegisteredAccountPort registeredAccountPort;

    @InjectMocks
    RegisteredExistingBankCustomerChecker checker;

    @Test
    @DisplayName("원장 고객 식별자가 일치하면 계좌를 보지 않고 ATH0303이다")
    void rejectsWhenExistingBankCustomerIdMatches() {
        given(availabilityPort.isExistingBankCustomerRegistered(BANK_CUSTOMER_ID))
                .willReturn(true);

        assertThatThrownBy(() -> checker.rejectIfRegistered(BANK_CUSTOMER_ID))
                .isInstanceOf(BusinessException.class)
                .satisfies(exception -> assertThat(((BusinessException) exception).getErrorCode())
                        .isEqualTo(SignupErrorCode.DUPLICATE_EXISTING_BANK_CUSTOMER));

        verify(accountsPort, never()).findAllByCustomerId(BANK_CUSTOMER_ID);
    }

    // 식별자 컬럼이 생기기 전에 가입한 고객은 그 값이 NULL 이라 첫 경로로는
    // 걸리지 않는다. 그 사람 계좌가 이미 등록돼 있으면 같은 사람으로 본다.
    @Test
    @DisplayName("식별자가 없어도 원장 고객의 계좌가 이미 등록됐으면 ATH0303이다")
    void rejectsLegacyCustomerByAlreadyRegisteredAccount() {
        given(availabilityPort.isExistingBankCustomerRegistered(BANK_CUSTOMER_ID))
                .willReturn(false);
        given(accountsPort.findAllByCustomerId(BANK_CUSTOMER_ID))
                .willReturn(List.of(account("110123456789"), account("110987654321")));
        given(registeredAccountPort.isRegistered("110123456789")).willReturn(false);
        given(registeredAccountPort.isRegistered("110987654321")).willReturn(true);

        assertThatThrownBy(() -> checker.rejectIfRegistered(BANK_CUSTOMER_ID))
                .isInstanceOf(BusinessException.class)
                .satisfies(exception -> assertThat(((BusinessException) exception).getErrorCode())
                        .isEqualTo(SignupErrorCode.DUPLICATE_EXISTING_BANK_CUSTOMER));
    }

    @Test
    @DisplayName("식별자도 계좌도 등록돼 있지 않으면 통과한다")
    void allowsUnregisteredExistingBankCustomer() {
        given(availabilityPort.isExistingBankCustomerRegistered(BANK_CUSTOMER_ID))
                .willReturn(false);
        given(accountsPort.findAllByCustomerId(BANK_CUSTOMER_ID)).willReturn(List.of(account("110123456789")));
        given(registeredAccountPort.isRegistered("110123456789")).willReturn(false);

        assertThatCode(() -> checker.rejectIfRegistered(BANK_CUSTOMER_ID)).doesNotThrowAnyException();
    }

    private ExistingBankAccountSnapshot account(String accountNumber) {
        return new ExistingBankAccountSnapshot(
                "BANK_ACCOUNT_" + accountNumber,
                accountNumber,
                "DEMAND_DEPOSIT",
                null,
                1_000_000L,
                "ACTIVE",
                "hash",
                LocalDate.of(2024, 1, 10),
                null);
    }
}
