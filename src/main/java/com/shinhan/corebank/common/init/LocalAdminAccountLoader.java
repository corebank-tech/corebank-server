package com.shinhan.corebank.common.init;

import java.nio.charset.StandardCharsets;
import javax.sql.DataSource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.stereotype.Component;

// 로컬 관리자 계정을 적재한다. QA 데모 데이터와 달리 qa-seed 로 운영 RDS 에 올리지 않는다 (PH-49a-①).
@Slf4j
@Component
@Profile("!phase2-seed & local")
@RequiredArgsConstructor
public class LocalAdminAccountLoader implements ApplicationRunner {

    private static final String SCRIPT = "db/seed/local-admin-accounts.sql";

    private final DataSource dataSource;

    @Override
    public void run(ApplicationArguments args) {
        log.info("Loading local admin accounts from classpath:{}", SCRIPT);
        ResourceDatabasePopulator populator = new ResourceDatabasePopulator(new ClassPathResource(SCRIPT));
        populator.setSqlScriptEncoding(StandardCharsets.UTF_8.name());
        populator.execute(dataSource);
    }
}
