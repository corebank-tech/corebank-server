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

    @Bean
    @ServiceConnection(name = "mysql")
    MySQLContainer mysqlContainer() {
        return new MySQLContainer(DockerImageName.parse("mysql:8.4.10"))
            .withDatabaseName("minicore")
            .withUrlParam("connectionTimeZone", "Asia/Seoul")
            .withUrlParam("forceConnectionTimeZoneToSession", "true")
            .withUrlParam("characterEncoding", "UTF-8")
            .withCommand(
                "--character-set-server=utf8mb4",
                "--collation-server=utf8mb4_0900_ai_ci",
                "--default-time-zone=+09:00")
            .withStartupTimeout(Duration.ofMinutes(3));
    }

    @Bean
    @ServiceConnection(name = "redis")
    GenericContainer<?> redisContainer() {
        return new GenericContainer<>(DockerImageName.parse("redis:7.4"))
                .withExposedPorts(6379)
                .withStartupTimeout(Duration.ofMinutes(2));
    }
}
