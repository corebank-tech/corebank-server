package com.shinhan.corebank;

import org.springframework.boot.data.redis.autoconfigure.DataRedisConnectionDetails;
import org.springframework.boot.jdbc.autoconfigure.JdbcConnectionDetails;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

@TestConfiguration(proxyBeanMethods = false)
class TestcontainersConfig {

    @Bean
    SharedTestContainers.Namespace testNamespace() {
        return SharedTestContainers.instance().newNamespace();
    }

    // 컨테이너를 Bean으로 노출하지 않아 컨텍스트 종료가 다른 테스트의 컨테이너를 멈추지 않는다.
    @Bean
    JdbcConnectionDetails jdbcConnectionDetails(SharedTestContainers.Namespace namespace) {
        return new JdbcConnectionDetails() {
            @Override
            public String getJdbcUrl() {
                return namespace.jdbcUrl();
            }

            @Override
            public String getUsername() {
                return namespace.username();
            }

            @Override
            public String getPassword() {
                return namespace.password();
            }
        };
    }

    @Bean
    DataRedisConnectionDetails redisConnectionDetails(SharedTestContainers.Namespace namespace) {
        return new DataRedisConnectionDetails() {
            @Override
            public Standalone getStandalone() {
                return Standalone.of(namespace.redisHost(), namespace.redisPort(), namespace.redisDatabase());
            }
        };
    }
}
