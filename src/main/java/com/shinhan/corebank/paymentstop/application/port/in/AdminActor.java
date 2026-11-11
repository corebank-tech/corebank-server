package com.shinhan.corebank.paymentstop.application.port.in;

// 요청한 관리자와 접속 IP. 감사 기록에 쓴다.
public record AdminActor(long adminCustomerId, String requestIp) {}
