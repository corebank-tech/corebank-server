package com.shinhan.corebank.auth.adapter.in.security;

import com.shinhan.corebank.auth.api.CustomerSessionInvalidator;
import com.shinhan.corebank.auth.application.event.LoginPasswordChangedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class LoginPasswordChangedSessionListener {

    private final CustomerSessionInvalidator sessionInvalidator;

    // 삭제 실패 시 비밀번호 변경도 롤백하여 기존 세션이 남는 부분 성공을 막는다.
    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void invalidateAll(LoginPasswordChangedEvent event) {
        sessionInvalidator.invalidateAll(event.customerId());
    }
}
