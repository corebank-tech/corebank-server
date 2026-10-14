package com.shinhan.corebank.common.init;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.shinhan.corebank.IntegrationTestSupport;
import java.nio.charset.StandardCharsets;
import java.util.List;
import javax.sql.DataSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

// 로컬 관리자 시드가 조회 전용·변경 가능 두 계정을 멱등하게 적재하는지 검증한다.
class LocalAdminAccountSeedIntegrationTest extends IntegrationTestSupport {

    private static final String ADMIN_SEED_SCRIPT = "db/seed/local-admin-accounts.sql";

    @Autowired
    private DataSource dataSource;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("관리자 시드를 재실행하면 손으로 바꾼 권한이 시드 조합으로 돌아온다")
    void restoresSeededPermissionsOnRerun() {
        loadAdminSeed();
        // 운영 중에 권한을 손으로 고친 상황을 만든다 — 하나를 지우고 없던 하나를 더한다.
        jdbcTemplate.update("DELETE ap FROM admin_permission ap JOIN customer c ON c.customer_id = ap.customer_id"
                + " WHERE c.user_id = 'adminreader' AND ap.permission = 'AUDIT_READ'");
        jdbcTemplate.update("INSERT INTO admin_permission (customer_id, permission, granted_at)"
                + " SELECT customer_id, 'GL_WRITE', NOW(6) FROM customer WHERE user_id = 'adminreader'");
        jdbcTemplate.update("UPDATE customer SET account_locked = TRUE WHERE user_id = 'adminreader'");

        loadAdminSeed();

        assertThat(permissionsOf("adminreader")).containsExactly("AUDIT_READ", "CUSTOMER_READ", "GL_READ");
        assertThat(permissionsOf("adminoperator"))
                .containsExactly("AUDIT_READ", "CUSTOMER_READ", "CUSTOMER_WRITE", "GL_READ", "GL_WRITE");
        // 다른 테스트가 만든 ADMIN 행에 흔들리지 않도록 시드 계정만 센다.
        assertThat(count("SELECT COUNT(*) FROM customer WHERE role = 'ADMIN'"
                        + " AND user_id IN ('adminreader', 'adminoperator')"))
                .isEqualTo(2);
        assertThat(count("SELECT COUNT(*) FROM customer WHERE user_id = 'adminreader' AND account_locked = FALSE"))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("로더가 관리자 시드 스크립트를 실제로 적재한다")
    void loadsSeedThroughLoader() {
        jdbcTemplate.update("DELETE ap FROM admin_permission ap JOIN customer c ON c.customer_id = ap.customer_id"
                + " WHERE c.user_id IN ('adminreader', 'adminoperator')");
        jdbcTemplate.update("DELETE FROM customer WHERE user_id IN ('adminreader', 'adminoperator')");

        new LocalAdminAccountLoader(dataSource).run(new DefaultApplicationArguments());

        assertThat(count("SELECT COUNT(*) FROM customer WHERE role = 'ADMIN'"
                        + " AND user_id IN ('adminreader', 'adminoperator')"))
                .isEqualTo(2);
        assertThat(permissionsOf("adminreader")).containsExactly("AUDIT_READ", "CUSTOMER_READ", "GL_READ");
    }

    @Test
    @DisplayName("권한 이름에 오타가 있으면 DB 가 거부한다")
    void rejectsUnknownPermission() {
        loadAdminSeed();

        // MySQL 은 CHECK 위반을 3819 로 돌려주고 Spring 은 이를 UncategorizedSQLException 으로 옮긴다.
        // 타입만 보면 다른 이유로 터져도 통과하므로 위반한 제약 이름까지 확인한다.
        assertThatThrownBy(
                        () -> jdbcTemplate.update("INSERT INTO admin_permission (customer_id, permission, granted_at)"
                                + " SELECT customer_id, 'GL_RAED', NOW(6) FROM customer WHERE user_id = 'adminreader'"))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("ck_admin_permission");
    }

    private void loadAdminSeed() {
        ResourceDatabasePopulator populator = new ResourceDatabasePopulator(new ClassPathResource(ADMIN_SEED_SCRIPT));
        populator.setSqlScriptEncoding(StandardCharsets.UTF_8.name());
        populator.execute(dataSource);
    }

    private List<String> permissionsOf(String userId) {
        return jdbcTemplate.queryForList(
                "SELECT ap.permission FROM admin_permission ap"
                        + " JOIN customer c ON c.customer_id = ap.customer_id"
                        + " WHERE c.user_id = ? ORDER BY ap.permission",
                String.class,
                userId);
    }

    private int count(String sql) {
        return jdbcTemplate.queryForObject(sql, Integer.class);
    }
}
