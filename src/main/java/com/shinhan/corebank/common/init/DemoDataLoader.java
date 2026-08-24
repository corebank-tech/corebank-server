package com.shinhan.corebank.common.init;

import javax.sql.DataSource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@Profile({"local", "qa-seed"})
public class DemoDataLoader implements ApplicationRunner {

    private final DataSource dataSource;
    private final boolean continueOnError;

    public DemoDataLoader(
            DataSource dataSource,
            @Value("${app.demo-data.continue-on-error:false}")
            boolean continueOnError
    ) {
        this.dataSource = dataSource;
        this.continueOnError = continueOnError;
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
    }
}
