package com.shinhan.corebank.common.ephemeralstore;

import static org.assertj.core.api.Assertions.assertThat;

import com.shinhan.corebank.RedisEphemeralStoreTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;

class EphemeralStoreRedisSwitchTest extends RedisEphemeralStoreTestSupport {

    @Autowired
    ApplicationContext context;

    @Test
    @DisplayName("provider=redis면 포트마다 Redis 어댑터만 하나씩 뜨고 MySQL 어댑터는 뜨지 않는다")
    void eachPortHasOnlyRedisAdapter() {
        EphemeralStorePorts.ALL.forEach(port -> assertThat(EphemeralStorePorts.implementationOf(context, port))
                .as(port.getSimpleName())
                .contains(".adapter.out.redis.")
                .endsWith("RedisAdapter"));
    }
}
