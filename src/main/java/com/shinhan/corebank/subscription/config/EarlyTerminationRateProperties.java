package com.shinhan.corebank.subscription.config;

import java.math.BigDecimal;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** 상품 공통 중도해지율 설정. 값은 0.10처럼 가입금리에 곱하는 비율이다. */
@ConfigurationProperties(prefix = "app.subscription.early-termination")
public record EarlyTerminationRateProperties(List<RateTier> rateTiers) {

    public EarlyTerminationRateProperties {
        if (rateTiers == null || rateTiers.isEmpty()) {
            throw new IllegalArgumentException("중도해지이율 설정이 필요합니다.");
        }
        rateTiers = List.copyOf(rateTiers);
    }

    public record RateTier(long minDays, BigDecimal multiplier) {}
}
