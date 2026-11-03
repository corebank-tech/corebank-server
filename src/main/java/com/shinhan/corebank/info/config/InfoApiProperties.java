package com.shinhan.corebank.info.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

// 정보계 API 접속 설정(PH-77 ③). baseUrl 이 비어 있으면 호출하지 않고 적재 이력 없음 응답을 유지한다.
@ConfigurationProperties(prefix = "app.info-api")
public record InfoApiProperties(String baseUrl, String secret, Duration connectTimeout, Duration readTimeout) {

    public InfoApiProperties {
        if (connectTimeout == null || connectTimeout.isZero() || connectTimeout.isNegative()) {
            throw new IllegalArgumentException("정보계 API 연결 제한 시간은 양수여야 합니다.");
        }
        if (readTimeout == null || readTimeout.isZero() || readTimeout.isNegative()) {
            throw new IllegalArgumentException("정보계 API 응답 제한 시간은 양수여야 합니다.");
        }
        if (baseUrl != null && !baseUrl.isBlank() && (secret == null || secret.isBlank())) {
            throw new IllegalArgumentException("정보계 API 주소를 설정하면 공유 비밀도 설정해야 합니다.");
        }
    }

    public boolean configured() {
        return baseUrl != null && !baseUrl.isBlank();
    }
}
