package com.shinhan.corebank.customer.application.port.in;

import jakarta.validation.constraints.NotNull;

// 관리자 권한을 부여·회수할 때 넘기는 행위자·대상·요청 IP. 조회 사유는 받지 않는다(PH-49b).
public record AdminPermissionCommand(@NotNull Long adminCustomerId, @NotNull Long targetCustomerId, String requestIp) {}
