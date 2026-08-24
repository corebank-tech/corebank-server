package com.shinhan.corebank.common.init;

import static org.assertj.core.api.Assertions.assertThat;

import com.shinhan.corebank.IntegrationTestSupport;
import javax.sql.DataSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.test.context.ActiveProfiles;

// 배포 QA 프로필이 상태별 고객·계좌 시드를 멱등하게 적재하는지 검증한다.
@ActiveProfiles({"test", "qa-seed"})
class QaSeedDataIntegrationTest extends IntegrationTestSupport {

    @Autowired
    private DataSource dataSource;

    @Test
    @DisplayName("QA 시드를 재실행해도 세 고객과 열한 계좌의 상태 조합이 유지된다")
    void loadsQaSeedIdempotently() {
        ResourceDatabasePopulator populator = new ResourceDatabasePopulator(
                new ClassPathResource("db/seed/local-demo-data.sql")
        );
        populator.setContinueOnError(false);
        populator.execute(dataSource);

        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        assertThat(count(jdbc, "SELECT COUNT(*) FROM customer WHERE user_id IN ('honggildong','kimminji','leeseojun')"))
                .isEqualTo(3);
        assertThat(count(jdbc, "SELECT COUNT(*) FROM account WHERE account_number BETWEEN '088100000001' AND '088100000009' OR account_number IN ('088200000001','088300000001')"))
                .isEqualTo(11);
        assertThat(count(jdbc, "SELECT COUNT(*) FROM account WHERE status = 'SUSPENDED' AND account_number IN ('088100000005','088100000007')"))
                .isEqualTo(2);
        assertThat(count(jdbc, "SELECT COUNT(*) FROM account WHERE status = 'CLOSED' AND account_number = '088100000008'"))
                .isEqualTo(1);
        assertThat(count(jdbc, "SELECT COUNT(*) FROM account WHERE withdrawal_registered = FALSE AND account_number = '088100000003'"))
                .isEqualTo(1);
    }

    private int count(JdbcTemplate jdbc, String sql) {
        return jdbc.queryForObject(sql, Integer.class);
    }
}
