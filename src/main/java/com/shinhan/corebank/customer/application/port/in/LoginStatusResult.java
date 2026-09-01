package com.shinhan.corebank.customer.application.port.in;

import java.time.LocalDateTime;

// 대시보드 접속현황에 표시할
public record LoginStatusResult(
        LocalDateTime previousLoginAt, String currentLoginIp, LocalDateTime lastTransactionAt) {}
