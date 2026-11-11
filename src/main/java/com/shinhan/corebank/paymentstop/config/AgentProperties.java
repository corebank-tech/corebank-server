package com.shinhan.corebank.paymentstop.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * 이의제기 Agent 연동 설정(Plan D15).
 *
 * @param enabled 킬스위치. false 면 워커가 작업을 가져가지 못한다(빈 응답)
 * @param workerKey 워커 서비스 키. 환경변수로만 넣는다. 비어 있으면 모든 내부 API 를 거부한다. 작업 토큰 서명 키로도 쓴다
 * @param tokenTtl 작업 토큰 유효 시간
 * @param lease 워커 점유 시간. 단계를 남길 때마다 연장한다
 * @param maxAttempts 이 횟수만큼 가져가고도 끝나지 않으면 FAILED
 */
@ConfigurationProperties(prefix = "app.payment-stop.agent")
public record AgentProperties(
        @DefaultValue("false") boolean enabled,
        @DefaultValue("") String workerKey,
        @DefaultValue("10m") Duration tokenTtl,
        @DefaultValue("5m") Duration lease,
        @DefaultValue("3") int maxAttempts) {

    public boolean hasWorkerKey() {
        return workerKey != null && !workerKey.isBlank();
    }
}
