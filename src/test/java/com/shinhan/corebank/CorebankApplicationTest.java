package com.shinhan.corebank;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mockStatic;

import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.boot.SpringApplication;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.core.env.MapPropertySource;

class CorebankApplicationTest {

    @Test
    @DisplayName("일회성 최소 시드 기동이 완료되면 main이 애플리케이션 컨텍스트를 닫는다")
    void mainClosesCompletedSeedContext() {
        String[] args = {"--spring.profiles.active=phase2-seed", "--app.phase2-seed.execute=true"};
        GenericApplicationContext context = contextWithSeedExecution(true, true);

        try (MockedStatic<SpringApplication> springApplication = mockStatic(SpringApplication.class)) {
            springApplication
                    .when(() -> SpringApplication.run(CorebankApplication.class, args))
                    .thenReturn(context);

            CorebankApplication.main(args);

            assertThat(context.isActive()).isFalse();
        }
    }

    @Test
    @DisplayName("일반 기동이면 main이 애플리케이션 컨텍스트를 유지한다")
    void mainKeepsRegularContextRunning() {
        String[] args = {"--spring.profiles.active=local"};
        GenericApplicationContext context = contextWithSeedExecution(false, false);

        try (MockedStatic<SpringApplication> springApplication = mockStatic(SpringApplication.class)) {
            springApplication
                    .when(() -> SpringApplication.run(CorebankApplication.class, args))
                    .thenReturn(context);

            CorebankApplication.main(args);

            assertThat(context.isActive()).isTrue();
        } finally {
            context.close();
        }
    }

    @Test
    @DisplayName("애플리케이션 진입점 클래스를 생성할 수 있다")
    void createsApplicationEntryPoint() {
        assertThat(new CorebankApplication()).isNotNull();
    }

    @Test
    @DisplayName("최소 시드 실행 플래그가 있으면 시작 완료 후 프로세스 컨텍스트를 닫는다")
    void closesCompletedSeedProcess() {
        GenericApplicationContext context = contextWithSeedExecution(true, true);

        boolean shouldExit = CorebankApplication.closeCompletedSeedProcess(context);

        assertThat(context.isActive()).isFalse();
        assertThat(shouldExit).isTrue();
    }

    @Test
    @DisplayName("최소 시드 실행 플래그가 없으면 일반 서버 컨텍스트를 유지한다")
    void keepsRegularServerRunning() {
        GenericApplicationContext context = contextWithSeedExecution(true, false);

        boolean shouldExit = CorebankApplication.closeCompletedSeedProcess(context);

        assertThat(context.isActive()).isTrue();
        assertThat(shouldExit).isFalse();
        context.close();
    }

    @Test
    @DisplayName("최소 시드 실행 플래그만 있고 전용 프로필이 없으면 일반 서버 컨텍스트를 유지한다")
    void keepsServerRunningWithoutSeedProfile() {
        GenericApplicationContext context = contextWithSeedExecution(false, true);

        boolean shouldExit = CorebankApplication.closeCompletedSeedProcess(context);

        assertThat(context.isActive()).isTrue();
        assertThat(shouldExit).isFalse();
        context.close();
    }

    private GenericApplicationContext contextWithSeedExecution(boolean profileActive, boolean enabled) {
        GenericApplicationContext context = new GenericApplicationContext();
        if (profileActive) {
            context.getEnvironment().setActiveProfiles("phase2-seed");
        }
        context.getEnvironment()
                .getPropertySources()
                .addFirst(new MapPropertySource(
                        "phase2SeedTest", Map.of("app.phase2-seed.execute", Boolean.toString(enabled))));
        context.refresh();
        return context;
    }
}
