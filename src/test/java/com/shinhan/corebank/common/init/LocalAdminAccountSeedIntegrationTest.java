package com.shinhan.corebank.common.init;

import static org.assertj.core.api.Assertions.assertThat;

import com.shinhan.corebank.IntegrationTestSupport;
import java.nio.charset.StandardCharsets;
import javax.sql.DataSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

// 로컬 관리자 시드가 조회 전용·변경 가능 두 계정을 멱등하게 적재하는지 검증한다.
class LocalAdminAccountSeedIntegrationTest extends IntegrationTestSupport {

    private static final String SCRIPT = "db/seed/local-admin-accounts.sql";

    @Autowired
    private DataSource dataSource;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("관리자 시드를 재실행해도 두 계정의 권한 조합이 유지된다")
    void loadsAdminAccountsIdempotently() {
        loadSeed();
        jdbcTemplate.update(
                "UPDATE customer SET permissions = 'GL_WRITE', account_locked = TRUE WHERE user_id = 'adminreader'");

        loadSeed();

        assertThat(jdbcTemplate.queryForObject(
                        "SELECT permissions FROM customer WHERE user_id = 'adminreader'", String.class))
                .isEqualTo("GL_READ,CUSTOMER_READ,AUDIT_READ");
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT permissions FROM customer WHERE user_id = 'adminoperator'", String.class))
                .isEqualTo("GL_READ,GL_WRITE,CUSTOMER_READ,CUSTOMER_WRITE,AUDIT_READ");
        // 다른 테스트가 만든 ADMIN 행에 흔들리지 않도록 시드 계정만 센다.
        assertThat(
                        count(
                                "SELECT COUNT(*) FROM customer WHERE role = 'ADMIN' AND user_id IN ('adminreader', 'adminoperator')"))
                .isEqualTo(2);
        assertThat(count("SELECT COUNT(*) FROM customer WHERE user_id = 'adminreader' AND account_locked = FALSE"))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("로더가 관리자 시드 스크립트를 실제로 적재한다")
    void loadsSeedThroughLoader() {
        jdbcTemplate.update("DELETE FROM customer WHERE user_id IN ('adminreader', 'adminoperator')");

        new LocalAdminAccountLoader(dataSource).run(new DefaultApplicationArguments());

        assertThat(
                        count(
                                "SELECT COUNT(*) FROM customer WHERE role = 'ADMIN' AND user_id IN ('adminreader', 'adminoperator')"))
                .isEqualTo(2);
    }

    private void loadSeed() {
        ResourceDatabasePopulator populator = new ResourceDatabasePopulator(new ClassPathResource(SCRIPT));
        populator.setSqlScriptEncoding(StandardCharsets.UTF_8.name());
        populator.execute(dataSource);
    }

    private int count(String sql) {
        return jdbcTemplate.queryForObject(sql, Integer.class);
    }
}
