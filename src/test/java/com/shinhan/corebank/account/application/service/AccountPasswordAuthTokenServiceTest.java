package com.shinhan.corebank.account.application.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.shinhan.corebank.account.api.AccountPasswordAuthTokenVerification;
import com.shinhan.corebank.account.application.port.out.AccountPasswordAuthTokenStorePort;
import com.shinhan.corebank.account.application.port.out.AccountPersistencePort;
import com.shinhan.corebank.account.domain.Account;
import com.shinhan.corebank.account.domain.AccountPasswordAuthTokenPayload;
import com.shinhan.corebank.common.exception.BusinessException;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("계좌비밀번호 인증 토큰 서비스 단위 테스트")
class AccountPasswordAuthTokenServiceTest {

    private final AccountPasswordAuthTokenStorePort tokenStorePort = mock(AccountPasswordAuthTokenStorePort.class);
    private final AccountPersistencePort accountPersistencePort = mock(AccountPersistencePort.class);
    private AccountPasswordAuthTokenService service;

    @BeforeEach
    void setUp() {
        service = new AccountPasswordAuthTokenService(tokenStorePort, accountPersistencePort);
    }

    @Test
    @DisplayName("동일 고객에게 발급된 토큰을 고객 기준으로 소비한다")
    void consumesTokenForCustomer() {
        given(tokenStorePort.consumeIfCustomerMatches("token", 1L)).willReturn(true);

        service.verifyAndConsume("token", 1L);

        verify(tokenStorePort).consumeIfCustomerMatches("token", 1L);
    }

    @Test
    @DisplayName("고객이 다르거나 토큰이 없으면 APW0102를 반환한다")
    void rejectsInvalidCustomerToken() {
        given(tokenStorePort.consumeIfCustomerMatches("token", 2L)).willReturn(false);

        assertInvalidToken(() -> service.verifyAndConsume("token", 2L));
    }

    @Test
    @DisplayName("고객 기준 검증 입력값이 없으면 Redis를 호출하지 않는다")
    void rejectsMissingCustomerVerificationInput() {
        assertInvalidToken(() -> service.verifyAndConsume((String) null, 1L));
        assertInvalidToken(() -> service.verifyAndConsume(" ", 1L));
        assertInvalidToken(() -> service.verifyAndConsume("token", null));

        verify(tokenStorePort, never()).consumeIfCustomerMatches(" ", 1L);
        verify(tokenStorePort, never()).consumeIfCustomerMatches("token", null);
    }

    @Test
    @DisplayName("기존 고객과 계좌 기준 검증·소비 동작을 유지한다")
    void keepsCustomerAndAccountVerification() {
        Account account = mock(Account.class);
        given(accountPersistencePort.findByAccountIdAndCustomerId(101L, 1L)).willReturn(Optional.of(account));
        given(account.isPasswordLocked()).willReturn(false);
        given(tokenStorePort.consumeIfMatches("token", new AccountPasswordAuthTokenPayload(1L, 101L)))
                .willReturn(true);

        service.verifyAndConsume(new AccountPasswordAuthTokenVerification("token", 1L, 101L));

        verify(tokenStorePort).consumeIfMatches("token", new AccountPasswordAuthTokenPayload(1L, 101L));
    }

    @Test
    @DisplayName("기존 고객·계좌 검증의 잘못된 입력을 모두 거부한다")
    void rejectsInvalidCustomerAndAccountVerificationInput() {
        assertInvalidToken(() -> service.verifyAndConsume((AccountPasswordAuthTokenVerification) null));
        assertInvalidToken(() -> service.verifyAndConsume(new AccountPasswordAuthTokenVerification(null, 1L, 101L)));
        assertInvalidToken(() -> service.verifyAndConsume(new AccountPasswordAuthTokenVerification(" ", 1L, 101L)));
        assertInvalidToken(
                () -> service.verifyAndConsume(new AccountPasswordAuthTokenVerification("token", null, 101L)));
        assertInvalidToken(() -> service.verifyAndConsume(new AccountPasswordAuthTokenVerification("token", 1L, null)));

        verify(accountPersistencePort, never())
                .findByAccountIdAndCustomerId(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("고객 소유 계좌가 없으면 APW0102를 반환한다")
    void rejectsUnknownOwnedAccount() {
        given(accountPersistencePort.findByAccountIdAndCustomerId(101L, 1L)).willReturn(Optional.empty());

        assertInvalidToken(() -> service.verifyAndConsume(new AccountPasswordAuthTokenVerification("token", 1L, 101L)));
    }

    @Test
    @DisplayName("계좌비밀번호가 잠긴 계좌의 토큰을 소비하지 않는다")
    void rejectsPasswordLockedAccount() {
        Account account = mock(Account.class);
        given(accountPersistencePort.findByAccountIdAndCustomerId(101L, 1L)).willReturn(Optional.of(account));
        given(account.isPasswordLocked()).willReturn(true);

        assertThatThrownBy(() -> service.verifyAndConsume(new AccountPasswordAuthTokenVerification("token", 1L, 101L)))
                .isInstanceOfSatisfying(
                        BusinessException.class, exception -> org.assertj.core.api.Assertions.assertThat(
                                        exception.getErrorCode().getCode())
                                .isEqualTo("APW0101"));
        verify(tokenStorePort, never()).consumeIfMatches("token", new AccountPasswordAuthTokenPayload(1L, 101L));
    }

    @Test
    @DisplayName("고객·계좌가 일치해도 토큰을 소비하지 못하면 APW0102를 반환한다")
    void rejectsUnconsumableCustomerAndAccountToken() {
        Account account = mock(Account.class);
        given(accountPersistencePort.findByAccountIdAndCustomerId(101L, 1L)).willReturn(Optional.of(account));
        given(account.isPasswordLocked()).willReturn(false);
        given(tokenStorePort.consumeIfMatches("token", new AccountPasswordAuthTokenPayload(1L, 101L)))
                .willReturn(false);

        assertInvalidToken(() -> service.verifyAndConsume(new AccountPasswordAuthTokenVerification("token", 1L, 101L)));
    }

    private void assertInvalidToken(org.assertj.core.api.ThrowableAssert.ThrowingCallable action) {
        assertThatThrownBy(action)
                .isInstanceOfSatisfying(
                        BusinessException.class, exception -> org.assertj.core.api.Assertions.assertThat(
                                        exception.getErrorCode().getCode())
                                .isEqualTo("APW0102"));
    }
}
