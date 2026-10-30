package com.shinhan.corebank.common.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

class SchedulingConfigProfileTest {

    @Test
    @DisplayName("phase2-seed 프로필에서는 스케줄러 설정을 등록하지 않는다")
    void excludesSchedulingFromSeedProcess() {
        try (AnnotationConfigApplicationContext context = context("phase2-seed")) {
            assertThat(context.getBeansOfType(SchedulingConfig.class)).isEmpty();
        }
    }

    @Test
    @DisplayName("일반 프로필에서는 기존 스케줄러 설정을 유지한다")
    void keepsSchedulingForNormalApplication() {
        try (AnnotationConfigApplicationContext context = context("local")) {
            assertThat(context.getBeansOfType(SchedulingConfig.class)).hasSize(1);
        }
    }

    private AnnotationConfigApplicationContext context(String profile) {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
        context.getEnvironment().setActiveProfiles(profile);
        context.register(SchedulingConfig.class);
        context.refresh();
        return context;
    }
}
