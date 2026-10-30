package com.shinhan.corebank.transfer.adapter.in.baseline;

import org.springframework.boot.context.properties.ConfigurationProperties;

// 배포 직후처럼 DB 캐시가 비어 있으면 첫 회차가 튀므로 앞 회차는 예열로 기록만 하고 통계에서 뺀다
@ConfigurationProperties(prefix = "app.ph38-baseline")
public record LedgerFullReconciliationBaselineProperties(int rounds, int warmupRounds) {

    public LedgerFullReconciliationBaselineProperties {
        if (rounds <= 0) {
            throw new IllegalArgumentException("rounds는 양수여야 합니다.");
        }
        if (warmupRounds < 0) {
            throw new IllegalArgumentException("warmupRounds는 0 이상이어야 합니다.");
        }
        if (warmupRounds >= rounds) {
            throw new IllegalArgumentException("예열을 빼고 통계를 낼 회차가 1회 이상 남아야 합니다.");
        }
    }
}
