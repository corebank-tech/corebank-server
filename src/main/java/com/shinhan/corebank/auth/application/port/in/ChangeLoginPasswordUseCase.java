package com.shinhan.corebank.auth.application.port.in;

public interface ChangeLoginPasswordUseCase {
    ChangeLoginPasswordResult change(ChangeLoginPasswordCommand command);
}
