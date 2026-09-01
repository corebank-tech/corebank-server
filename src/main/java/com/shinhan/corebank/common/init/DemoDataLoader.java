package com.shinhan.corebank.common.init;

import javax.sql.DataSource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@Profile({"local", "qa-seed"})
public class DemoDataLoader implements ApplicationRunner {

    private final DataSource dataSource;
    private final boolean continueOnError;
    private final boolean validateAfterLoad;

    public DemoDataLoader(
            DataSource dataSource,
            @Value("${app.demo-data.continue-on-error:false}") boolean continueOnError,
            @Value("${app.demo-data.validate-after-load:false}") boolean validateAfterLoad) {
        this.dataSource = dataSource;
        this.continueOnError = continueOnError;
        this.validateAfterLoad = validateAfterLoad;
    }

    @Override
    public void run(ApplicationArguments args) {
        Resource resource = new ClassPathResource("db/seed/local-demo-data.sql");
        if (!resource.exists()) {
            log.warn("Demo data script not found: classpath:db/seed/local-demo-data.sql");
            return;
        }
        log.info("Loading demo data from classpath:db/seed/local-demo-data.sql");
        ResourceDatabasePopulator populator = new ResourceDatabasePopulator(resource);
        // 로컬은 개발 편의를 유지하고, 배포 QA 시드는 SQL 오류에서 즉시 실패시킨다.
        populator.setContinueOnError(continueOnError);
        populator.execute(dataSource);
        if (validateAfterLoad) {
            validateQaSeedOwnership();
        }
    }

    private void validateQaSeedOwnership() {
        Integer accountCount = new JdbcTemplate(dataSource)
                .queryForObject(
                        """
                SELECT COUNT(*)
                FROM account a
                JOIN customer c ON c.customer_id = a.customer_id
                WHERE (c.user_id = 'honggildong'
                         AND c.email = 'honggildong@example.com'
                         AND a.account_number BETWEEN '088100000001' AND '088100000005')
                   OR (c.user_id = 'kimminji'
                         AND c.email = 'kimminji@example.com'
                         AND a.account_number IN (
                             '088100000006', '088100000007', '088100000008',
                             '088200000001', '088300000001'
                         ))
                   OR (c.user_id = 'leeseojun'
                         AND c.email = 'leeseojun@example.com'
                         AND a.account_number = '088100000009')
                """,
                        Integer.class);
        if (accountCount == null || accountCount != 11) {
            throw new IllegalStateException("QA demo data ownership validation failed");
        }
    }
}
