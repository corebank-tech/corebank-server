package com.shinhan.corebank.auth.adapter.in.web;

import com.shinhan.corebank.auth.application.port.in.ChangeLoginPasswordResult;
import java.time.OffsetDateTime;

public record LoginPasswordChangeResponse(Long customerId, OffsetDateTime passwordChangedAt) {
    public static LoginPasswordChangeResponse from(ChangeLoginPasswordResult result) {
        return new LoginPasswordChangeResponse(result.customerId(), result.passwordChangedAt());
    }
}
