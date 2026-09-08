package com.shinhan.corebank;

import java.time.Duration;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.mysql.MySQLContainer;
import org.testcontainers.utility.DockerImageName;

@TestConfiguration(proxyBeanMethods = false)
class TestcontainersConfig {

    // 싱글턴 컨테이너. @Bean 이 매번 새 인스턴스를 만들면 스프링 컨텍스트마다 컨테이너가
    // 복제된다 - mock 조합이 다르면 컨텍스트가 갈라지므로 실제로 수십 개가 동시에 뜬다.
    // static 으로 한 번만 만들어 전 컨텍스트가 공유한다. JVM 이 끝나면 Ryuk 이 정리한다.
    private static final MySQLContainer MYSQL = new MySQLContainer(DockerImageName.parse("mysql:8.4.10"))
            .withDatabaseName("minicore")
            .withUrlParam("connectionTimeZone", "Asia/Seoul")
            .withUrlParam("forceConnectionTimeZoneToSession", "true")
            .withUrlParam("characterEncoding", "UTF-8")
            .withCommand(
                    "--character-set-server=utf8mb4",
                    "--collation-server=utf8mb4_0900_ai_ci",
                    "--default-time-zone=+09:00")
            .withStartupTimeout(Duration.ofMinutes(3));

    private static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:7.4"))
            .withExposedPorts(6379)
            .withStartupTimeout(Duration.ofMinutes(2));

    static {
        MYSQL.start();
        REDIS.start();
    }

    @Bean
    @ServiceConnection(name = "mysql")
    MySQLContainer mysqlContainer() {
        return MYSQL;
    }

    @Bean
    @ServiceConnection(name = "redis")
    GenericContainer<?> redisContainer() {
        return REDIS;
    }
}
