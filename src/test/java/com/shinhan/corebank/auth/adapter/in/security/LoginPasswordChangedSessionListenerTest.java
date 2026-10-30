package com.shinhan.corebank.auth.adapter.in.security;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

import com.shinhan.corebank.auth.api.CustomerSessionInvalidator;
import com.shinhan.corebank.auth.application.event.LoginPasswordChangedEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;

class LoginPasswordChangedSessionListenerTest {

    private final CustomerSessionInvalidator invalidator = mock(CustomerSessionInvalidator.class);
    private final LoginPasswordChangedSessionListener listener = new LoginPasswordChangedSessionListener(invalidator);

    @Test
    @DisplayName("비밀번호를 변경한 고객의 현재 세션과 다른 세션을 모두 만료시킨다")
    void expiresAllSessionsForCustomer() {
        listener.invalidateAll(new LoginPasswordChangedEvent(1L));
        verify(invalidator).invalidateAll(1L);
        verifyNoMoreInteractions(invalidator);
    }

    @Test
    @DisplayName("삭제 실패를 전파해 비밀번호 변경 트랜잭션도 롤백한다")
    void propagatesInvalidationFailure() {
        doThrow(new DataAccessResourceFailureException("unavailable"))
                .when(invalidator)
                .invalidateAll(1L);
        assertThatThrownBy(() -> listener.invalidateAll(new LoginPasswordChangedEvent(1L)))
                .isInstanceOf(DataAccessResourceFailureException.class);
    }
}
