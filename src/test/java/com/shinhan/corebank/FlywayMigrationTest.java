package com.shinhan.corebank;

import static org.assertj.core.api.Assertions.assertThat;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class FlywayMigrationTest extends IntegrationTestSupport {
    @Autowired
    Flyway flyway;

    @Test
    @DisplayName("빈 DB에 전체 마이그레이션이 적용되고 엔티티와 일치한다")
    void migrateFromScratch() {
        assertThat(flyway.info().current()).isNotNull();
        assertThat(flyway.info().pending()).isEmpty();
    }
}
