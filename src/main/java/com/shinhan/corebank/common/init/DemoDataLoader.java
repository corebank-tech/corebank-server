package com.shinhan.corebank.common.init;

import javax.sql.DataSource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@Profile("local")
@RequiredArgsConstructor
public class DemoDataLoader implements ApplicationRunner {

    private final DataSource dataSource;

    @Override
    public void run(ApplicationArguments args) {
        Resource resource = new ClassPathResource("db/seed/local-demo-data.sql");
        if (!resource.exists()) {
            log.warn("Demo data script not found: classpath:db/seed/local-demo-data.sql");
            return;
        }
        log.info("Loading demo data from classpath:db/seed/local-demo-data.sql");
        ResourceDatabasePopulator populator = new ResourceDatabasePopulator(resource);
        populator.setContinueOnError(true);
        populator.execute(dataSource);
    }
}
