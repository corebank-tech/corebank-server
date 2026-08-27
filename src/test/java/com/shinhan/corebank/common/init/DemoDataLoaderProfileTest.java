package com.shinhan.corebank.common.init;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.function.Supplier;
import javax.sql.DataSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

class DemoDataLoaderProfileTest {

    @Test
    @DisplayName("prod 단독 프로필에서는 QA 시드 로더를 등록하지 않는다")
    void doesNotRegisterLoaderWithProdOnly() {
        try (AnnotationConfigApplicationContext context = contextWithProfiles("prod")) {
            assertThat(context.getBeansOfType(DemoDataLoader.class)).isEmpty();
        }
    }

    @Test
    @DisplayName("prod와 qa-seed 프로필을 함께 사용하면 QA 시드 로더를 등록한다")
    void registersLoaderWithProdAndQaSeed() {
        try (AnnotationConfigApplicationContext context = contextWithProfiles("prod", "qa-seed")) {
            assertThat(context.getBeansOfType(DemoDataLoader.class)).hasSize(1);
        }
    }

    private AnnotationConfigApplicationContext contextWithProfiles(String... profiles) {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
        context.getEnvironment().setActiveProfiles(profiles);
        Supplier<DataSource> dataSourceSupplier = DriverManagerDataSource::new;
        context.registerBean("dataSource", DataSource.class, dataSourceSupplier);
        context.register(DemoDataLoader.class);
        context.refresh();
        return context;
    }
}
