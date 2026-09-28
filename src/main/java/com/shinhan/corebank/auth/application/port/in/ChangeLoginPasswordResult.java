package com.shinhan.corebank.auth.application.port.in;

import java.time.OffsetDateTime;

public record ChangeLoginPasswordResult(Long customerId, OffsetDateTime passwordChangedAt) {}
