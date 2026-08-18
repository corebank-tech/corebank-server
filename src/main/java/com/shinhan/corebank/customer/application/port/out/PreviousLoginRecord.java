package com.shinhan.corebank.customer.application.port.out;

import java.time.LocalDateTime;

public record PreviousLoginRecord(LocalDateTime loginAt, String loginIp) {
}
