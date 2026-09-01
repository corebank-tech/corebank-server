package com.shinhan.corebank.signup.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.shinhan.corebank.common.exception.BusinessException;
import com.shinhan.corebank.common.exception.CommonErrorCode;
import com.shinhan.corebank.customer.api.RegisteredCustomer;
import com.shinhan.corebank.signup.application.port.in.CompleteSignupCommand;
import com.shinhan.corebank.signup.application.port.out.ExistingBankCustomerAccountsPort;
import com.shinhan.corebank.signup.application.port.out.ExistingBankCustomerProfilePort;
import com.shinhan.corebank.signup.application.port.out.SignupCustomerAvailabilityPort;
import com.shinhan.corebank.signup.application.port.out.TempSignupTokenClaimPort;
import com.shinhan.corebank.signup.domain.exception.SignupErrorCode;
import com.shinhan.corebank.signup.domain.model.AgreedTerm;
import com.shinhan.corebank.signup.domain.model.ExistingBankAccountSnapshot;
import com.shinhan.corebank.signup.domain.model.ExistingBankCustomerProfile;
import com.shinhan.corebank.signup.domain.model.TempSignupTokenPayload;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

// 회원가입 완료 서비스의 토큰 선점과 고객·약관·계좌 등록 흐름을 검증한다.
@ExtendWith(MockitoExtension.class)
class SignupCompletionServiceTest {

    private static final String TOKEN = "TEMP_SIGNUP_test";
    private static final Instant NOW = Instant.parse("2026-08-20T06:00:00Z");

    @Mock
    TempSignupTokenClaimPort tokenClaimPort;

    @Mock
    SignupCustomerAvailabilityPort availabilityPort;

    @Mock
    ExistingBankCustomerProfilePort profilePort;

    @Mock
    ExistingBankCustomerAccountsPort accountsPort;

    @Mock
    RegisteredExistingBankCustomerChecker registrationChecker;

    @Mock
    SignupCompletionTransactionService transactionService;

    SignupCompletionService service;

    @BeforeEach
    void setUp() {
        service = new SignupCompletionService(
                tokenClaimPort,
                availabilityPort,
                registrationChecker,
                profilePort,
                accountsPort,
                transactionService,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void completesSignupAndPermanentlyConsumesClaim() {
        given(tokenClaimPort.claim(eq(TOKEN), any())).willReturn(Optional.of(payload()));
        given(profilePort.findByCustomerId("BANK_CUSTOMER_001")).willReturn(Optional.of(profile()));
        given(accountsPort.findAllByCustomerId("BANK_CUSTOMER_001")).willReturn(accounts());
        given(transactionService.register(any(), any())).willReturn(new RegisteredCustomer(101L, "honggildong"));

        var result = service.complete(new CompleteSignupCommand(TOKEN));

        assertThat(result.customerId()).isEqualTo(101L);
        assertThat(result.userId()).isEqualTo("honggildong");
        verify(transactionService).register(any(), any());
        verify(tokenClaimPort).complete(eq(TOKEN), any());
        verify(tokenClaimPort, never()).release(eq(TOKEN), any());
    }

    @Test
    void rejectsMissingExpiredOrReusedTempToken() {
        given(tokenClaimPort.claim(eq(TOKEN), any())).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.complete(new CompleteSignupCommand(TOKEN)))
                .isInstanceOf(BusinessException.class)
                .satisfies(exception -> assertThat(((BusinessException) exception).getErrorCode())
                        .isEqualTo(CommonErrorCode.INVALID_INPUT));

        verify(transactionService, never()).register(any(), any());
    }

    @Test
    void restoresTokenWhenDatabaseTransactionFails() {
        given(tokenClaimPort.claim(eq(TOKEN), any())).willReturn(Optional.of(payload()));
        given(profilePort.findByCustomerId("BANK_CUSTOMER_001")).willReturn(Optional.of(profile()));
        given(accountsPort.findAllByCustomerId("BANK_CUSTOMER_001")).willReturn(accounts());
        given(transactionService.register(any(), any())).willThrow(new IllegalStateException("DB failure"));

        assertThatThrownBy(() -> service.complete(new CompleteSignupCommand(TOKEN)))
                .isInstanceOf(IllegalStateException.class);

        verify(tokenClaimPort).release(eq(TOKEN), any());
        verify(tokenClaimPort, never()).complete(eq(TOKEN), any());
    }

    @Test
    void rejectsAlreadyRegisteredExistingBankCustomerAndRestoresToken() {
        given(tokenClaimPort.claim(eq(TOKEN), any())).willReturn(Optional.of(payload()));
        doThrow(new BusinessException(SignupErrorCode.DUPLICATE_EXISTING_BANK_CUSTOMER))
                .when(registrationChecker)
                .rejectIfRegistered("BANK_CUSTOMER_001");

        assertThatThrownBy(() -> service.complete(new CompleteSignupCommand(TOKEN)))
                .isInstanceOf(BusinessException.class)
                .satisfies(exception -> assertThat(((BusinessException) exception).getErrorCode())
                        .isEqualTo(SignupErrorCode.DUPLICATE_EXISTING_BANK_CUSTOMER));

        verify(tokenClaimPort).release(eq(TOKEN), any());
        verify(transactionService, never()).register(any(), any());
    }

    @Test
    void rejectsDuplicateUserIdAndRestoresToken() {
        given(tokenClaimPort.claim(eq(TOKEN), any())).willReturn(Optional.of(payload()));
        given(availabilityPort.isUserIdTaken("honggildong")).willReturn(true);

        assertThatThrownBy(() -> service.complete(new CompleteSignupCommand(TOKEN)))
                .isInstanceOf(BusinessException.class)
                .satisfies(exception -> assertThat(((BusinessException) exception).getErrorCode())
                        .isEqualTo(SignupErrorCode.DUPLICATE_USER_ID));

        verify(tokenClaimPort).release(eq(TOKEN), any());
        verify(transactionService, never()).register(any(), any());
    }

    @Test
    void rejectsDuplicateEmailAndRestoresToken() {
        given(tokenClaimPort.claim(eq(TOKEN), any())).willReturn(Optional.of(payload()));
        given(availabilityPort.isEmailTaken("hong@corebank.example.com")).willReturn(true);

        assertThatThrownBy(() -> service.complete(new CompleteSignupCommand(TOKEN)))
                .isInstanceOf(BusinessException.class)
                .satisfies(exception -> assertThat(((BusinessException) exception).getErrorCode())
                        .isEqualTo(SignupErrorCode.DUPLICATE_EMAIL));

        verify(tokenClaimPort).release(eq(TOKEN), any());
        verify(transactionService, never()).register(any(), any());
    }

    @Test
    void restoresTokenWhenExistingCustomerProfileIsMissing() {
        given(tokenClaimPort.claim(eq(TOKEN), any())).willReturn(Optional.of(payload()));
        given(profilePort.findByCustomerId("BANK_CUSTOMER_001")).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.complete(new CompleteSignupCommand(TOKEN)))
                .isInstanceOf(IllegalStateException.class);

        verify(tokenClaimPort).release(eq(TOKEN), any());
        verify(transactionService, never()).register(any(), any());
    }

    @Test
    void restoresTokenWhenExistingCustomerHasNoAccounts() {
        given(tokenClaimPort.claim(eq(TOKEN), any())).willReturn(Optional.of(payload()));
        given(profilePort.findByCustomerId("BANK_CUSTOMER_001")).willReturn(Optional.of(profile()));
        given(accountsPort.findAllByCustomerId("BANK_CUSTOMER_001")).willReturn(List.of());

        assertThatThrownBy(() -> service.complete(new CompleteSignupCommand(TOKEN)))
                .isInstanceOf(IllegalStateException.class);

        verify(tokenClaimPort).release(eq(TOKEN), any());
        verify(transactionService, never()).register(any(), any());
    }

    @Test
    void returnsSuccessWhenClaimCleanupFailsAfterDatabaseCommit() {
        given(tokenClaimPort.claim(eq(TOKEN), any())).willReturn(Optional.of(payload()));
        given(profilePort.findByCustomerId("BANK_CUSTOMER_001")).willReturn(Optional.of(profile()));
        given(accountsPort.findAllByCustomerId("BANK_CUSTOMER_001")).willReturn(accounts());
        given(transactionService.register(any(), any())).willReturn(new RegisteredCustomer(101L, "honggildong"));
        doThrow(new IllegalStateException("Redis failure")).when(tokenClaimPort).complete(eq(TOKEN), any());

        var result = service.complete(new CompleteSignupCommand(TOKEN));

        assertThat(result.customerId()).isEqualTo(101L);
        verify(tokenClaimPort, never()).release(eq(TOKEN), any());
    }

    private TempSignupTokenPayload payload() {
        return new TempSignupTokenPayload(
                List.of(new AgreedTerm("1", "1.0")),
                "BANK_CUSTOMER_001",
                "BANK_ACCOUNT_001",
                "honggildong",
                "$2y$10$hash",
                "hong@corebank.example.com",
                "01012345678",
                NOW);
    }

    private ExistingBankCustomerProfile profile() {
        return new ExistingBankCustomerProfile("BANK_CUSTOMER_001", "홍길동", LocalDate.of(1990, 1, 1));
    }

    private List<ExistingBankAccountSnapshot> accounts() {
        return List.of(new ExistingBankAccountSnapshot(
                "BANK_ACCOUNT_001",
                "110123456789",
                "DEMAND_DEPOSIT",
                null,
                1_000_000L,
                "ACTIVE",
                "$2y$10$hash",
                LocalDate.of(2024, 1, 10),
                null));
    }
}
