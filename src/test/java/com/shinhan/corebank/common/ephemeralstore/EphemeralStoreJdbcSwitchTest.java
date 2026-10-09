package com.shinhan.corebank.common.ephemeralstore;

import static org.assertj.core.api.Assertions.assertThat;

import com.shinhan.corebank.IntegrationTestSupport;
import java.util.Arrays;
import java.util.Objects;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;

class EphemeralStoreJdbcSwitchTest extends IntegrationTestSupport {

    @Autowired
    ApplicationContext context;

    @Test
    @DisplayName("기본값(provider=jdbc)이면 포트마다 MySQL 어댑터만 하나씩 뜬다")
    void eachPortHasOnlyPersistenceAdapter() {
        EphemeralStorePorts.ALL.forEach(port -> assertThat(EphemeralStorePorts.implementationOf(context, port))
                .as(port.getSimpleName())
                .contains(".adapter.out.persistence.")
                .endsWith("PersistenceAdapter"));
    }

    @Test
    @DisplayName("기본값(provider=jdbc)이면 Redis 어댑터 빈이 하나도 없다")
    void noRedisAdapterBean() {
        assertThat(Arrays.stream(context.getBeanDefinitionNames())
                        .map(context::getType)
                        .filter(Objects::nonNull)
                        .map(Class::getName)
                        .filter(className -> className.contains(".adapter.out.redis.")))
                .isEmpty();
    }
}
