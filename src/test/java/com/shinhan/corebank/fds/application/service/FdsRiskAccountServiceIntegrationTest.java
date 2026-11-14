package com.shinhan.corebank.fds.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.shinhan.corebank.IntegrationTestSupport;
import com.shinhan.corebank.common.exception.BusinessException;
import com.shinhan.corebank.fds.application.service.FdsRiskAccountService.RiskRow;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

class FdsRiskAccountServiceIntegrationTest extends IntegrationTestSupport {
    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    FdsRiskAccountService service;

    @Test
    void loadsActivatesAndLooksUpDatasetIdempotently() {
        String version = version();
        String hash = "a".repeat(64);
        List<RiskRow> rows = List.of(
                new RiskRow("088", "110-039-200000", "합성 위험 계좌"), new RiskRow("020", "12345678901", "합성 위험 계좌 2"));

        service.loadAndActivate(version, 2, hash, rows);
        service.loadAndActivate(version, 2, hash, rows);

        var match = service.check("088", "110039200000");
        assertThat(match.status()).isEqualTo("MATCH");
        assertThat(match.datasetVersion()).isEqualTo(version);
        assertThat(match.riskAccountId()).isNotNull();
        assertThat(service.check("088", "99999999999").status()).isEqualTo("NO_MATCH");
        assertThat(service.checkForAction("020", "12345678901").status()).isEqualTo("MATCH");
        assertThat(jdbc.queryForObject(
                        "SELECT COUNT(*) FROM fds_risk_account WHERE dataset_version=?", Integer.class, version))
                .isEqualTo(2);
    }

    @Test
    void rejectsInvalidManifestRowsDuplicatesAndChangedContent() {
        String version = version();
        RiskRow row = new RiskRow("088", "110039200000", "합성");

        assertThatThrownBy(() -> service.loadAndActivate(version, 2, "bad", List.of(row)))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> service.loadAndActivate(version, 2, "b".repeat(64), List.of(row, row)))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> service.loadAndActivate(
                        version, 1, "b".repeat(64), List.of(new RiskRow("XX", "110039200000", "합성"))))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() ->
                        service.loadAndActivate(version, 1, "b".repeat(64), List.of(new RiskRow("088", "123", "합성"))))
                .isInstanceOf(BusinessException.class);

        service.loadAndActivate(version, 1, "b".repeat(64), List.of(row));
        assertThatThrownBy(() -> service.loadAndActivate(version, 1, "c".repeat(64), List.of(row)))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void returnsUnavailableWhenNoActiveDatasetExists() {
        jdbc.update("DELETE FROM fds_risk_active_version");
        assertThat(service.check("088", "110039200000").status()).isEqualTo("UNAVAILABLE");
    }

    @Test
    void reactivationAndActionLookupUseOneLockOrderWithoutDeadlock() throws Exception {
        String version = version();
        String hash = "d".repeat(64);
        List<RiskRow> rows = List.of(new RiskRow("088", "110039200000", "합성"));
        service.loadAndActivate(version, 1, hash, rows);
        CyclicBarrier start = new CyclicBarrier(2);

        try (var workers = Executors.newFixedThreadPool(2)) {
            var reload = workers.submit(() -> {
                start.await();
                service.loadAndActivate(version, 1, hash, rows);
                return true;
            });
            var lookup = workers.submit(() -> {
                start.await();
                return service.checkForAction("088", "110039200000");
            });

            assertThat(reload.get(10, TimeUnit.SECONDS)).isTrue();
            assertThat(lookup.get(10, TimeUnit.SECONDS).status()).isEqualTo("MATCH");
        }
    }

    private static String version() {
        return "test-risk-" + UUID.randomUUID().toString().substring(0, 8);
    }
}
