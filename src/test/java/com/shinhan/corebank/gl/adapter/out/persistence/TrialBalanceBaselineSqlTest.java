package com.shinhan.corebank.gl.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("시산표 베이스라인 SQL 과 API 쿼리 대조")
class TrialBalanceBaselineSqlTest {

    private static final Path BASELINE_SQL = Path.of("perf/sql/ph28_trial_balance_baseline.sql");

    @Test
    @DisplayName("측정 SQL 의 EXPLAIN ANALYZE 대상이 저장소 쿼리와 같다 — 다르면 잰 숫자가 API 의 숫자가 아니다")
    void baselineMeasuresTheQueryTheApiRuns() throws IOException {
        String file = Files.readString(BASELINE_SQL, StandardCharsets.UTF_8);
        String measured = file.substring(file.indexOf("EXPLAIN ANALYZE") + "EXPLAIN ANALYZE".length());
        measured = measured.substring(0, measured.indexOf(';'))
                .replace("@from_date", ":fromDate")
                .replace("@to_date", ":toDate");

        assertThat(normalize(measured)).isEqualTo(normalize(GlJournalEntryJpaRepository.SUM_BY_ACCOUNT_SQL));
    }

    private static String normalize(String sql) {
        return sql.replaceAll("\\s+", " ").trim();
    }
}
