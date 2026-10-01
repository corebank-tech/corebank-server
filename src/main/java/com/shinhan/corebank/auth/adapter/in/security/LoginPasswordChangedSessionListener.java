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

    // expireNow는 멱등적인 JVM 메모리 변경이므로 별도 재시도 없이 실패를 전파해 조용한 부분 성공을 막는다.
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
