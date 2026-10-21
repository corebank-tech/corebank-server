package com.shinhan.infoetl.adapter.out.jdbc;

import static org.assertj.core.api.Assertions.assertThat;

import com.shinhan.infoetl.InfoEtlTestDatabase;
import com.shinhan.infoetl.ReplicaFixtures;
import java.sql.SQLException;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class JdbcReplicaCutoffAdapterTest {

    private static final LocalDate D = LocalDate.of(2026, 10, 23);

    private final JdbcReplicaCutoffAdapter adapter = new JdbcReplicaCutoffAdapter(InfoEtlTestDatabase::replica);

    @BeforeEach
    void resetReplica() throws SQLException {
        InfoEtlTestDatabase.resetReplica();
    }

    @Test
    @DisplayName("CLOSED 행이 있는 날짜만 마감된 것으로 본다")
    void isClosedOnlyForCutoffRows() throws SQLException {
        ReplicaFixtures.insertCutoff(D);

        assertThat(adapter.isClosed(D)).isTrue();
        assertThat(adapter.isClosed(D.plusDays(3))).isFalse();
        assertThat(adapter.isClosed(D.minusDays(1))).isFalse();
    }

    @Test
    @DisplayName("가장 최근 CLOSED 날짜를 돌려주고, 하나도 없으면 비어 있다")
    void latestClosedDate() throws SQLException {
        assertThat(adapter.latestClosedDate()).isEmpty();

        ReplicaFixtures.insertCutoff(D);
        ReplicaFixtures.insertCutoff(D.plusDays(3));
        ReplicaFixtures.insertCutoff(D.minusDays(1));

        assertThat(adapter.latestClosedDate()).contains(D.plusDays(3));
    }
}
