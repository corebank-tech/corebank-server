package com.shinhan.corebank.auth.adapter.in.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.shinhan.corebank.auth.api.AuthenticatedCustomer;
import com.shinhan.corebank.auth.application.event.LoginPasswordChangedEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.session.SessionRegistryImpl;

class LoginPasswordChangedSessionListenerTest {

    private final SessionRegistryImpl sessionRegistry = new SessionRegistryImpl();
    private final LoginPasswordChangedSessionListener listener =
            new LoginPasswordChangedSessionListener(sessionRegistry);

    @Test
    @DisplayName("비밀번호를 변경한 고객의 현재 세션과 다른 세션을 모두 만료시킨다")
    void expiresAllSessionsForCustomer() {
        AuthenticatedCustomer changedCustomer = new AuthenticatedCustomer(1L, "user01", "홍길동");
        sessionRegistry.registerNewSession("current-session", changedCustomer);
        sessionRegistry.registerNewSession("other-session", changedCustomer);

        listener.invalidateAll(new LoginPasswordChangedEvent(1L));

        assertThat(sessionRegistry.getSessionInformation("current-session").isExpired())
                .isTrue();
        assertThat(sessionRegistry.getSessionInformation("other-session").isExpired())
                .isTrue();
    }

    @Test
    @DisplayName("다른 고객의 세션은 유지한다")
    void keepsOtherCustomerSessions() {
        AuthenticatedCustomer changedCustomer = new AuthenticatedCustomer(1L, "user01", "홍길동");
        AuthenticatedCustomer otherCustomer = new AuthenticatedCustomer(2L, "user02", "김신한");
        sessionRegistry.registerNewSession("changed-session", changedCustomer);
        sessionRegistry.registerNewSession("other-customer-session", otherCustomer);

        listener.invalidateAll(new LoginPasswordChangedEvent(1L));

        assertThat(sessionRegistry.getSessionInformation("changed-session").isExpired())
                .isTrue();
        assertThat(sessionRegistry
                        .getSessionInformation("other-customer-session")
                        .isExpired())
                .isFalse();
    }
}
