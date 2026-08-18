package com.shinhan.corebank.customer.application.port.in;

import java.time.LocalDateTime;

public record LoginStatusResult(LocalDateTime previousLoginAt, String previousLoginIp) {
}
