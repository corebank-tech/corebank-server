package com.shinhan.corebank.common.config;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.auditing.DateTimeProvider;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@Configuration
@EnableJpaAuditing(dateTimeProviderRef = "auditingDateTimeProvider")
public class JpaAuditingConfig {
    @Bean
    public Clock clock() {
        return Clock.system(ZoneId.of("Asia/Seoul"));
    }

    // @EnableJpaAuditing의 기본 DateTimeProvider는 이 Clock 빈을 무시하고
    // 시스템 기본 zone으로 now()를 계산한다. @CreatedDate/@LastModifiedDate가
    // 실제로 이 Clock을 쓰게 하려면 명시적으로 연결해야 한다.
    @Bean(name = "auditingDateTimeProvider")
    public DateTimeProvider auditingDateTimeProvider(Clock clock) {
        return () -> Optional.of(LocalDateTime.now(clock));
    }
}
