package com.shinhan.corebank.auth.application.port.in;

import java.time.OffsetDateTime;

public record ResetPasswordResult(Long customerId, OffsetDateTime changedAt) {}
