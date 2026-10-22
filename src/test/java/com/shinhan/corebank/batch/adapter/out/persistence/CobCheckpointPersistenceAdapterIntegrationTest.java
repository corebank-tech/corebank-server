package com.shinhan.corebank.batch.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.shinhan.corebank.IntegrationTestSupport;
import com.shinhan.corebank.batch.application.port.out.CobCheckpointPort;
import com.shinhan.corebank.batch.application.port.out.CobCheckpointPort.IncompleteCob;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

// REQUIRES_NEW로 커밋되는 쓰기가 검증 대상이라 @Transactional 을 걸지 않고 테스트 날짜 행만 지운다
class CobCheckpointPersistenceAdapterIntegrationTest extends IntegrationTestSupport {

    // 시드·다른 테스트와 겹치지 않는 먼 미래 날짜(#663)
    private static final LocalDate COB_DATE = LocalDate.of(2663, 1, 2);
    private static final LocalDateTime NOW = LocalDateTime.of(2663, 1, 2, 23, 30);

    @Autowired
    CobCheckpointPort cobCheckpointPort;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @BeforeEach
    @AfterEach
    void cleanUp() {
        jdbcTemplate.update("DELETE FROM cob_checkpoint WHERE cob_date IN (?, ?)", COB_DATE, COB_DATE.plusDays(1));
    }

    private String statusOf(String stepName) {
        return jdbcTemplate.queryForObject(
                "SELECT status FROM cob_checkpoint WHERE cob_date = ? AND step_name = ?",
                String.class,
                COB_DATE,
                stepName);
    }

    private int rowCount() {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM cob_checkpoint WHERE cob_date = ?", Integer.class, COB_DATE);
    }

    @Test
    @DisplayName("initialize는 전환과 스텝 행을 모두 PENDING으로 남긴다")
    void initialize_insertsAllRowsAsPending() {
        cobCheckpointPort.initialize(COB_DATE, List.of("business-date-advance", "a", "b"), NOW);

        assertThat(rowCount()).isEqualTo(3);
        assertThat(statusOf("business-date-advance")).isEqualTo("PENDING");
        assertThat(statusOf("a")).isEqualTo("PENDING");
        assertThat(statusOf("b")).isEqualTo("PENDING");
    }

    @Test
    @DisplayName("initialize를 다시 불러도 행이 늘지 않고, 이미 SUCCESS인 행을 PENDING으로 되돌리지 않는다")
    void initialize_isIdempotent_andKeepsSuccess() {
        cobCheckpointPort.initialize(COB_DATE, List.of("a", "b"), NOW);
        cobCheckpointPort.markSuccess(COB_DATE, "a", NOW);

        cobCheckpointPort.initialize(COB_DATE, List.of("a", "b"), NOW.plusMinutes(5));

        assertThat(rowCount()).isEqualTo(2);
        assertThat(statusOf("a")).isEqualTo("SUCCESS");
        assertThat(statusOf("b")).isEqualTo("PENDING");
    }

    @Test
    @DisplayName("markSuccess는 해당 스텝만 SUCCESS로 바꾸고 갱신 시각을 남긴다")
    void markSuccess_updatesOnlyThatStep() {
        cobCheckpointPort.initialize(COB_DATE, List.of("a", "b"), NOW);

        cobCheckpointPort.markSuccess(COB_DATE, "a", NOW.plusMinutes(1));

        assertThat(statusOf("a")).isEqualTo("SUCCESS");
        assertThat(statusOf("b")).isEqualTo("PENDING");
        LocalDateTime updatedAt = jdbcTemplate.queryForObject(
                "SELECT updated_at FROM cob_checkpoint WHERE cob_date = ? AND step_name = 'a'",
                LocalDateTime.class,
                COB_DATE);
        assertThat(updatedAt).isEqualTo(NOW.plusMinutes(1));
    }

    @Test
    @DisplayName("행이 없는 스텝에 markSuccess를 불러도 예외 없이 아무 행도 만들지 않는다")
    void markSuccess_withoutRow_doesNothing() {
        cobCheckpointPort.markSuccess(COB_DATE, "missing", NOW);

        assertThat(rowCount()).isZero();
    }

    @Test
    @DisplayName("findIncomplete는 PENDING이 남은 날짜만 오래된 순으로, 남은 스텝 이름과 함께 돌려준다")
    void findIncomplete_returnsPendingDatesOldestFirst() {
        LocalDate later = COB_DATE.plusDays(1);
        cobCheckpointPort.initialize(later, List.of("a", "b"), NOW);
        cobCheckpointPort.initialize(COB_DATE, List.of("a", "b"), NOW);
        cobCheckpointPort.markSuccess(COB_DATE, "a", NOW);

        List<IncompleteCob> result = cobCheckpointPort.findIncomplete(List.of("a", "b"));

        assertThat(result)
                .filteredOn(c -> c.cobDate().equals(COB_DATE) || c.cobDate().equals(later))
                .containsExactly(new IncompleteCob(COB_DATE, Set.of("b")), new IncompleteCob(later, Set.of("a", "b")));
    }

    @Test
    @DisplayName("findIncomplete는 모든 스텝이 SUCCESS인 날짜와 알려지지 않은 스텝의 PENDING은 돌려주지 않는다")
    void findIncomplete_ignoresCompletedDatesAndUnknownSteps() {
        cobCheckpointPort.initialize(COB_DATE, List.of("a", "removed-step"), NOW);
        cobCheckpointPort.markSuccess(COB_DATE, "a", NOW);

        List<IncompleteCob> result = cobCheckpointPort.findIncomplete(List.of("a"));

        assertThat(result).noneMatch(c -> c.cobDate().equals(COB_DATE));
    }
}
