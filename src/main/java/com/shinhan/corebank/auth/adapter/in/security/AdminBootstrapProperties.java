package com.shinhan.corebank.auth.adapter.in.security;

import java.util.List;
import java.util.Objects;
import org.springframework.boot.context.properties.ConfigurationProperties;

// 관리자 인증(PH-49a) 전까지 /admin/** 접근을 허용할 고객 PK 목록. 비어 있으면 전부 거부한다.
@ConfigurationProperties(prefix = "app.security.admin")
public record AdminBootstrapProperties(List<Long> bootstrapCustomerIds) {

    public AdminBootstrapProperties {
        // "1,,2"처럼 빈 원소가 있으면 바인더가 null을 넣으므로 걸러낸다.
        bootstrapCustomerIds = bootstrapCustomerIds == null
                ? List.of()
                : bootstrapCustomerIds.stream()
                        .filter(Objects::nonNull)
                        .distinct()
                        .toList();
    }
}
