package com.shinhan.corebank.customer.api;

import java.time.LocalDateTime;

// 로그인 성공 시 최근 접속 일시와 IP를 저장하기 위한 명령
public record RecordLoginSuccessCommand(Long customerId, LocalDateTime loginAt, String loginIp) {}
