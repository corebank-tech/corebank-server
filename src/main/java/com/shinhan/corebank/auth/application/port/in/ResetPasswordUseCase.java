package com.shinhan.corebank.auth.application.port.in;

public interface ResetPasswordUseCase {
    ResetPasswordResult reset(ResetPasswordCommand command);

    Long resolveCustomerId(String requestId);
}
