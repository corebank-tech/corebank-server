package com.shinhan.corebank.account.application.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.shinhan.corebank.account.api.AccountPasswordAuthTokenVerification;
import com.shinhan.corebank.account.application.port.out.AccountPasswordAuthTokenStorePort;
import com.shinhan.corebank.account.application.port.out.AccountPersistencePort;
import com.shinhan.corebank.account.domain.AccountPasswordAuthTokenPayload;
import com.shinhan.corebank.common.exception.BusinessException;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("계좌비밀번호 인증 토큰 서비스 단위 테스트")
class AccountPasswordAuthTokenServiceTest {

    private static final Long CUSTOMER_ID = 1L;
    private static final Long ACCOUNT_ID = 101L;
    private static final String TOKEN = "token";

    private final AccountPasswordAuthTokenStorePort tokenStorePort = mock(AccountPasswordAuthTokenStorePort.class);

    private final AccountPersistencePort accountPersistencePort = mock(AccountPersistencePort.class);

    private AccountPasswordAuthTokenService service;

    @BeforeEach
    void setUp() {
        service = new AccountPasswordAuthTokenService(tokenStorePort, accountPersistencePort);
    }

    @Test
    @DisplayName("고객 기준 인증 토큰을 정상 소비한다")
    void consumesTokenForCustomer() {
        given(tokenStorePort.consumeIfCustomerMatches(TOKEN, CUSTOMER_ID)).willReturn(true);

        service.verifyAndConsume(TOKEN, CUSTOMER_ID);

        verify(tokenStorePort).consumeIfCustomerMatches(TOKEN, CUSTOMER_ID);
    }

    @Test
    @DisplayName("고객 기준 토큰이 일치하지 않으면 APW0102")
    void rejectsInvalidCustomerToken() {
        given(tokenStorePort.consumeIfCustomerMatches(TOKEN, CUSTOMER_ID)).willReturn(false);

        assertInvalidToken(() -> service.verifyAndConsume(TOKEN, CUSTOMER_ID));
    }

    @Test
    @DisplayName("고객 기준 필수 입력이 없으면 토큰을 소비하지 않는다")
    void rejectsMissingCustomerInput() {
        assertInvalidToken(() -> service.verifyAndConsume((String) null, CUSTOMER_ID));
        assertInvalidToken(() -> service.verifyAndConsume(" ", CUSTOMER_ID));
        assertInvalidToken(() -> service.verifyAndConsume(TOKEN, null));

        verify(tokenStorePort, never()).consumeIfCustomerMatches(any(), any());
    }

    @Test
    @DisplayName("소유 계좌의 잠금 상태만 조회하고 토큰을 정상 소비한다")
    void consumesValidAccountToken() {
        given(accountPersistencePort.findPasswordLockedByAccountIdAndCustomerId(ACCOUNT_ID, CUSTOMER_ID))
                .willReturn(Optional.of(false));

        given(tokenStorePort.consumeIfMatches(TOKEN, payload())).willReturn(true);

        service.verifyAndConsume(verification());

        verify(accountPersistencePort).findPasswordLockedByAccountIdAndCustomerId(ACCOUNT_ID, CUSTOMER_ID);

        verify(accountPersistencePort, never()).findByAccountIdAndCustomerId(any(), any());

        verify(tokenStorePort).consumeIfMatches(TOKEN, payload());
    }

    @Test
    @DisplayName("계좌 기준 검증 입력이 잘못되면 조회와 소비를 하지 않는다")
    void rejectsInvalidAccountVerificationInput() {
        assertInvalidToken(() -> service.verifyAndConsume((AccountPasswordAuthTokenVerification) null));

        assertInvalidToken(() ->
                service.verifyAndConsume(new AccountPasswordAuthTokenVerification(null, CUSTOMER_ID, ACCOUNT_ID)));

        assertInvalidToken(
                () -> service.verifyAndConsume(new AccountPasswordAuthTokenVerification(" ", CUSTOMER_ID, ACCOUNT_ID)));

        assertInvalidToken(
                () -> service.verifyAndConsume(new AccountPasswordAuthTokenVerification(TOKEN, null, ACCOUNT_ID)));

        assertInvalidToken(
                () -> service.verifyAndConsume(new AccountPasswordAuthTokenVerification(TOKEN, CUSTOMER_ID, null)));

        verify(accountPersistencePort, never()).findPasswordLockedByAccountIdAndCustomerId(any(), any());

        verify(tokenStorePort, never()).consumeIfMatches(any(), any());
    }

    @Test
    @DisplayName("존재하지 않거나 타인 소유 계좌면 APW0102")
    void rejectsUnknownOwnedAccount() {
        given(accountPersistencePort.findPasswordLockedByAccountIdAndCustomerId(ACCOUNT_ID, CUSTOMER_ID))
                .willReturn(Optional.empty());

        assertInvalidToken(() -> service.verifyAndConsume(verification()));

        verify(tokenStorePort, never()).consumeIfMatches(any(), any());
    }

    @Test
    @DisplayName("계좌비밀번호가 잠긴 계좌는 APW0101, 토큰 소비 안 함")
    void rejectsPasswordLockedAccount() {
        given(accountPersistencePort.findPasswordLockedByAccountIdAndCustomerId(ACCOUNT_ID, CUSTOMER_ID))
                .willReturn(Optional.of(true));

        assertThatThrownBy(() -> service.verifyAndConsume(verification()))
                .isInstanceOfSatisfying(
                        BusinessException.class, exception -> org.assertj.core.api.Assertions.assertThat(
                                        exception.getErrorCode().getCode())
                                .isEqualTo("APW0101"));

        verify(tokenStorePort, never()).consumeIfMatches(any(), any());
    }

    @Test
    @DisplayName("잘못되거나 이미 소비된 계좌 토큰은 APW0102")
    void rejectsUnconsumableToken() {
        given(accountPersistencePort.findPasswordLockedByAccountIdAndCustomerId(ACCOUNT_ID, CUSTOMER_ID))
                .willReturn(Optional.of(false));

        given(tokenStorePort.consumeIfMatches(TOKEN, payload())).willReturn(false);

        assertInvalidToken(() -> service.verifyAndConsume(verification()));
    }

    @Test
    @DisplayName("계좌 인증 토큰은 한 번만 소비할 수 있다")
    void tokenIsSingleUse() {
        given(accountPersistencePort.findPasswordLockedByAccountIdAndCustomerId(ACCOUNT_ID, CUSTOMER_ID))
                .willReturn(Optional.of(false));

        given(tokenStorePort.consumeIfMatches(TOKEN, payload())).willReturn(true, false);

        service.verifyAndConsume(verification());

        assertInvalidToken(() -> service.verifyAndConsume(verification()));
    }

    private AccountPasswordAuthTokenVerification verification() {
        return new AccountPasswordAuthTokenVerification(TOKEN, CUSTOMER_ID, ACCOUNT_ID);
    }

    private AccountPasswordAuthTokenPayload payload() {
        return new AccountPasswordAuthTokenPayload(CUSTOMER_ID, ACCOUNT_ID);
    }

    private void assertInvalidToken(org.assertj.core.api.ThrowableAssert.ThrowingCallable action) {

        assertThatThrownBy(action)
                .isInstanceOfSatisfying(
                        BusinessException.class, exception -> org.assertj.core.api.Assertions.assertThat(
                                        exception.getErrorCode().getCode())
                                .isEqualTo("APW0102"));
    }
}
