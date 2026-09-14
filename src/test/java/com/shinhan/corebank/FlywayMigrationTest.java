package com.shinhan.corebank;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.stream.IntStream;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

class FlywayMigrationTest extends IntegrationTestSupport {
    @Autowired
    Flyway flyway;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("빈 DB에 전체 마이그레이션이 적용되고 엔티티와 일치한다")
    void migrateFromScratch() {
        assertThat(flyway.info().current()).isNotNull();
        assertThat(flyway.info().pending()).isEmpty();
    }

    // #379 센서. add_ledger_partition은 "이미 있으면 건너뛴다" 분기가 있어 CALL 성공만으로는
    // 생성을 보장하지 못하므로, information_schema로 실제 파티션을 확인한다.
    @Test
    @DisplayName("ledger_entry에 2027-12까지의 월별 파티션과 pmax가 존재한다 (#379)")
    void ledgerEntryHasMonthlyPartitionsThrough2027() {
        List<String> partitions = jdbcTemplate.queryForList(
                """
                SELECT PARTITION_NAME FROM information_schema.PARTITIONS
                 WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'ledger_entry'
                """,
                String.class);

        List<String> expected = IntStream.rangeClosed(1, 12)
                .mapToObj(month -> "p2027%02d".formatted(month))
                .toList();

        assertThat(partitions).containsAll(expected).contains("pmax");
    }
}
