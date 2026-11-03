package com.shinhan.corebank.transfer.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 지연이체(PH-06) 설정. enabled는 새로 보류할지만 정한다. 이미 보류된 건은 꺼져 있어도 해제 스케줄러가 실행한다.
 *
 * @param enabled    꺼져 있으면 훅 C가 고객 설정을 읽지 않고 바로 실행한다(transfer_seam.md §9-5 규칙 6)
 * @param stuckAfter EXECUTING으로 이만큼 지난 보류는 멈춘 것으로 보고 다시 실행한다
 * @param batchSize  해제 스케줄러가 한 번에 꺼내는 건수
 */
@ConfigurationProperties(prefix = "app.delayed-transfer")
public record DelayedTransferProperties(boolean enabled, Duration stuckAfter, int batchSize) {}
