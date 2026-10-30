package com.shinhan.corebank.common.audit;

import java.time.LocalDateTime;
import org.springframework.boot.context.properties.ConfigurationProperties;

// rowHashSince: 이 시각 전에 생긴 감사 행은 해시가 없어도 정상이라 검증하지 않는다. 해시를 배포한 시각 이후에 정한다.
@ConfigurationProperties(prefix = "app.audit.integrity")
public record AuditIntegrityProperties(LocalDateTime rowHashSince) {}
