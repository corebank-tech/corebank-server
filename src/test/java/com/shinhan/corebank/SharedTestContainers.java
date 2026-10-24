package com.shinhan.corebank;

import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import org.testcontainers.mysql.MySQLContainer;
import org.testcontainers.utility.DockerImageName;

// 실행 간 영구 재사용 없이 JVM당 MySQL 하나만 시작하고 종료는 Ryuk에 맡긴다.
final class SharedTestContainers {

    private static SharedTestContainers sharedInstance;
    private final AtomicInteger namespaceSequence = new AtomicInteger();
    private final MySQLContainer mysql;

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

        mysql.start();
    }

    static synchronized SharedTestContainers instance() {
        if (sharedInstance == null) {
            sharedInstance = new SharedTestContainers();
        }
        return sharedInstance;
    }

    Namespace newNamespace() {
        int index = namespaceSequence.incrementAndGet();
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
                mysql.getPassword());
    }

    // 컨텍스트별 DB 스키마를 분리해 독립 커밋 데이터의 교차 오염을 막는다.
    record Namespace(String jdbcUrl, String username, String password) {}
}
