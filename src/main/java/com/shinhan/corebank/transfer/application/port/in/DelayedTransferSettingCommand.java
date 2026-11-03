package com.shinhan.corebank.transfer.application.port.in;

/**
 * 지연이체 설정 변경 요청.
 *
 * @param accountPasswordAuthToken 보호를 약하게 하는 변경일 때만 필요하다. 그 밖에는 null이어도 된다
 */
public record DelayedTransferSettingCommand(
        long customerId, boolean enabled, long thresholdAmount, int delayHours, String accountPasswordAuthToken) {}
