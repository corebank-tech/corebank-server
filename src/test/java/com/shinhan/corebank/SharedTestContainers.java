package com.shinhan.corebank;

import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.mysql.MySQLContainer;
import org.testcontainers.utility.DockerImageName;

// 실행 간 영구 재사용 없이 JVM당 MySQL·Redis 한 쌍만 시작하고 종료는 Ryuk에 맡긴다.
final class SharedTestContainers {

    private static final int REDIS_DATABASES = 512;
    private static SharedTestContainers sharedInstance;
    private final AtomicInteger namespaceSequence = new AtomicInteger();
    private final MySQLContainer mysql;
    private final GenericContainer<?> redis;

    private SharedTestContainers() {
        mysql = new MySQLContainer(DockerImageName.parse("mysql:8.4.10"))
                .withDatabaseName("minicore")
                .withUrlParam("connectionTimeZone", "Asia/Seoul")
                .withUrlParam("forceConnectionTimeZoneToSession", "true")
                .withUrlParam("characterEncoding", "UTF-8")
                .withCommand(
                        "--character-set-server=utf8mb4",
                        "--collation-server=utf8mb4_0900_ai_ci",
                        "--default-time-zone=+09:00")
                .withLabel("com.shinhan.corebank.test-infrastructure", "shared")
                .withStartupTimeout(Duration.ofMinutes(3));
        redis = new GenericContainer<>(DockerImageName.parse("redis:7.4"))
                .withExposedPorts(6379)
                .withCommand("redis-server", "--databases", Integer.toString(REDIS_DATABASES))
                .withLabel("com.shinhan.corebank.test-infrastructure", "shared")
                .withStartupTimeout(Duration.ofMinutes(2));

        mysql.start();
        try {
            redis.start();
        } catch (RuntimeException failure) {
            mysql.stop();
            throw failure;
        }
    }

    static synchronized SharedTestContainers instance() {
        if (sharedInstance == null) {
            sharedInstance = new SharedTestContainers();
        }
        return sharedInstance;
    }

    Namespace newNamespace() {
        int index = namespaceSequence.incrementAndGet();
        if (index >= REDIS_DATABASES) {
            throw new IllegalStateException("테스트 컨텍스트 수가 Redis 격리 DB 한도를 초과했습니다.");
        }

        String database = "corebank_test_" + index;
        try (var connection = DriverManager.getConnection(mysql.getJdbcUrl(), "root", mysql.getPassword());
                var statement = connection.createStatement()) {
            statement.execute("CREATE DATABASE `" + database + "` CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci");
            statement.execute("GRANT ALL PRIVILEGES ON `" + database + "`.* TO '" + mysql.getUsername() + "'@'%'");
        } catch (SQLException exception) {
            throw new IllegalStateException("테스트 전용 스키마 생성에 실패했습니다.", exception);
        }

        return new Namespace(
                mysql.getJdbcUrl().replace("/" + mysql.getDatabaseName(), "/" + database),
                mysql.getUsername(),
                mysql.getPassword(),
                redis.getHost(),
                redis.getMappedPort(6379),
                index);
    }

    // 컨텍스트별 DB 스키마와 Redis DB를 분리해 독립 커밋 및 TTL 데이터의 교차 오염을 막는다.
    record Namespace(
            String jdbcUrl, String username, String password, String redisHost, int redisPort, int redisDatabase) {}
}
