package com.shinhan.corebank.account.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.shinhan.corebank.account.api.AccountIdentityVerification;
import com.shinhan.corebank.account.api.AccountIdentityVerificationResult;
import com.shinhan.corebank.account.api.AccountIdentityVerificationStatus;
import com.shinhan.corebank.account.application.port.out.AccountPersistencePort;
import com.shinhan.corebank.account.domain.Account;
import com.shinhan.corebank.account.domain.AccountPasswordAttemptResult;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

// 아이디 찾기용 계좌 소유권과 기존 비밀번호 잠금 정책의 연결을 검증한다.
class AccountIdentityVerificationServiceTest {

    private final AccountPersistencePort persistencePort = mock(AccountPersistencePort.class);
    private final AccountPasswordVerificationProcessor processor = mock(AccountPasswordVerificationProcessor.class);
    private final AccountIdentityVerificationService service =
            new AccountIdentityVerificationService(persistencePort, processor);

    @Test
    @DisplayName("후보 고객이 소유한 계좌의 올바른 비밀번호를 검증한다")
    void verifiesOwnedAccountPassword() {
        Account account = account(101L, 2L);
        given(persistencePort.findByAccountNumber("110550051877")).willReturn(Optional.of(account));
        given(processor.verify(2L, 101L, "1234")).willReturn(new AccountPasswordAttemptResult(101L, true, 0, 5, false));

        AccountIdentityVerificationResult result = service.verify(verification(Set.of(1L, 2L)));

        assertThat(result.status()).isEqualTo(AccountIdentityVerificationStatus.VERIFIED);
        assertThat(result.customerId()).isEqualTo(2L);
    }

    @Test
    @DisplayName("계좌가 없거나 후보 고객 소유가 아니면 비밀번호를 비교하지 않는다")
    void hidesMissingOrForeignAccount() {
        Account foreignAccount = account(101L, 9L);
        given(persistencePort.findByAccountNumber("110550051877")).willReturn(Optional.of(foreignAccount));

        AccountIdentityVerificationResult result = service.verify(verification(Set.of(1L, 2L)));

        assertThat(result.status()).isEqualTo(AccountIdentityVerificationStatus.INFORMATION_MISMATCH);
        assertThat(result.customerId()).isNull();
        verify(processor, never())
                .verify(
                        org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("비밀번호 불일치와 잠금 상태를 구분해 반환한다")
    void returnsPasswordAttemptStatus() {
        Account account = account(101L, 1L);
        given(persistencePort.findByAccountNumber("110550051877")).willReturn(Optional.of(account));
        given(processor.verify(1L, 101L, "1234")).willReturn(new AccountPasswordAttemptResult(101L, false, 5, 0, true));

        AccountIdentityVerificationResult result = service.verify(verification(Set.of(1L)));

        assertThat(result.status()).isEqualTo(AccountIdentityVerificationStatus.LOCKED);
    }

    @Test
    @DisplayName("계좌비밀번호가 틀렸지만 남은 횟수가 있으면 불일치 상태를 반환한다")
    void returnsPasswordMismatchBeforeLock() {
        Account account = account(101L, 1L);
        given(persistencePort.findByAccountNumber("110550051877")).willReturn(Optional.of(account));
        given(processor.verify(1L, 101L, "1234"))
                .willReturn(new AccountPasswordAttemptResult(101L, false, 2, 3, false));

        AccountIdentityVerificationResult result = service.verify(verification(Set.of(1L)));

        assertThat(result.status()).isEqualTo(AccountIdentityVerificationStatus.PASSWORD_MISMATCH);
    }

    private Account account(Long accountId, Long customerId) {
        Account account = mock(Account.class);
        given(account.getAccountId()).willReturn(accountId);
        given(account.getCustomerId()).willReturn(customerId);
        return account;
    }

    private AccountIdentityVerification verification(Set<Long> customerIds) {
        return new AccountIdentityVerification(customerIds, "110550051877", "1234");
    }
}
