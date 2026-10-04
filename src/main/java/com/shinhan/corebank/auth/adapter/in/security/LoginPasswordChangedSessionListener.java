package com.shinhan.corebank.auth.adapter.in.security;

import com.shinhan.corebank.auth.api.AuthenticatedCustomer;
import com.shinhan.corebank.auth.application.event.LoginPasswordChangedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class LoginPasswordChangedSessionListener {

    private final SessionRegistry sessionRegistry;

    // expireNow는 메모리 플래그 변경이라 재시도하지 않으며 실패는 AFTER_COMMIT ERROR 로그로 확인한다.
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void invalidateAll(LoginPasswordChangedEvent event) {
        sessionRegistry.getAllPrincipals().stream()
                .filter(AuthenticatedCustomer.class::isInstance)
                .map(AuthenticatedCustomer.class::cast)
                .filter(customer -> customer.customerId().equals(event.customerId()))
                .flatMap(customer -> sessionRegistry.getAllSessions(customer, false).stream())
                .forEach(session -> session.expireNow());
    }
}
