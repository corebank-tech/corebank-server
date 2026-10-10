package com.shinhan.corebank;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mockStatic;

import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.boot.SpringApplication;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.core.env.MapPropertySource;

class CorebankApplicationTest {

    private static final String DEVTOOLS_RESTART_PROPERTY = "spring.devtools.restart.enabled";

    @Test
    @DisplayName("일회성 최소 시드 기동이 완료되면 main이 애플리케이션 컨텍스트를 닫는다")
    void mainClosesCompletedSeedContext() {
        String[] args = {"--spring.profiles.active=phase2-seed", "--app.phase2-seed.minimum.execute=true"};
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

        boolean shouldExit = Phase2SeedProcessRunner.closeCompletedSeedProcess(context);

        assertThat(context.isActive()).isFalse();
        assertThat(shouldExit).isTrue();
    }

    @Test
    @DisplayName("최소 시드 실행 플래그가 없으면 일반 서버 컨텍스트를 유지한다")
    void keepsRegularServerRunning() {
        GenericApplicationContext context = contextWithSeedExecution(true, false);

        boolean shouldExit = Phase2SeedProcessRunner.closeCompletedSeedProcess(context);

        assertThat(context.isActive()).isTrue();
        assertThat(shouldExit).isFalse();
        context.close();
    }

    @Test
    @DisplayName("최소 시드 실행 플래그만 있고 전용 프로필이 없으면 일반 서버 컨텍스트를 유지한다")
    void keepsServerRunningWithoutSeedProfile() {
        GenericApplicationContext context = contextWithSeedExecution(false, true);

        boolean shouldExit = Phase2SeedProcessRunner.closeCompletedSeedProcess(context);

        assertThat(context.isActive()).isTrue();
        assertThat(shouldExit).isFalse();
        context.close();
    }

    @Test
    @DisplayName("최소 시드 명령이면 스프링 실행 전에 DevTools 재시작을 끈다")
    void disablesDevToolsRestartForSeedCommand() {
        String previous = System.getProperty(DEVTOOLS_RESTART_PROPERTY);
        try {
            System.clearProperty(DEVTOOLS_RESTART_PROPERTY);

            boolean disabled = Phase2SeedProcessRunner.disableDevToolsRestartForSeed(
                    new String[] {"--spring.profiles.active=local,phase2-seed", "--app.phase2-seed.minimum.execute=true"
                    });

            assertThat(disabled).isTrue();
            assertThat(System.getProperty(DEVTOOLS_RESTART_PROPERTY)).isEqualTo("false");
        } finally {
            restoreSystemProperty(previous);
        }
    }

    @Test
    @DisplayName("최소 시드 프로필이나 실행 플래그가 빠지면 DevTools 설정을 바꾸지 않는다")
    void keepsDevToolsSettingForRegularCommand() {
        String previous = System.getProperty(DEVTOOLS_RESTART_PROPERTY);
        try {
            System.clearProperty(DEVTOOLS_RESTART_PROPERTY);

            assertThat(Phase2SeedProcessRunner.disableDevToolsRestartForSeed(
                            new String[] {"--spring.profiles.active=local"}))
                    .isFalse();
            assertThat(Phase2SeedProcessRunner.disableDevToolsRestartForSeed(
                            new String[] {"--spring.profiles.active=local,phase2-seed"}))
                    .isFalse();
            assertThat(Phase2SeedProcessRunner.disableDevToolsRestartForSeed(
                            new String[] {"--app.phase2-seed.minimum.execute=true"}))
                    .isFalse();
            assertThat(System.getProperty(DEVTOOLS_RESTART_PROPERTY)).isNull();
        } finally {
            restoreSystemProperty(previous);
        }
    }

    @Test
    @DisplayName("PH-11 베이스라인 실행이 완료되면 프로세스 컨텍스트를 닫는다")
    void closesCompletedPh11BaselineProcess() {
        GenericApplicationContext context = contextWithPh11BaselineExecution(true, true);

        boolean shouldExit = Phase2SeedProcessRunner.closeCompletedSeedProcess(context);

        assertThat(context.isActive()).isFalse();
        assertThat(shouldExit).isTrue();
    }

    @Test
    @DisplayName("PH-11 베이스라인 실행 플래그가 없으면 서버 컨텍스트를 유지한다")
    void keepsContextWithoutPh11BaselineExecutionFlag() {
        GenericApplicationContext context = contextWithPh11BaselineExecution(true, false);

        boolean shouldExit = Phase2SeedProcessRunner.closeCompletedSeedProcess(context);

        assertThat(context.isActive()).isTrue();
        assertThat(shouldExit).isFalse();

        context.close();
    }

    @Test
    @DisplayName("PH-11 베이스라인 명령이면 스프링 실행 전에 DevTools 재시작을 끈다")
    void disablesDevToolsRestartForPh11BaselineCommand() {
        String previous = System.getProperty(DEVTOOLS_RESTART_PROPERTY);

        try {
            System.clearProperty(DEVTOOLS_RESTART_PROPERTY);

            boolean disabled = Phase2SeedProcessRunner.disableDevToolsRestartForSeed(
                    new String[] {"--spring.profiles.active=local,ph11-baseline", "--app.ph11-baseline.execute=true"});

            assertThat(disabled).isTrue();
            assertThat(System.getProperty(DEVTOOLS_RESTART_PROPERTY)).isEqualTo("false");
        } finally {
            restoreSystemProperty(previous);
        }
    }

    @Test
    @DisplayName("최소 시드와 대량 시드 플래그를 동시에 켜면 기동을 거부한다")
    void rejectsBothSeedExecutionFlags() {
        String[] args = {
            "--spring.profiles.active=phase2-seed",
            "--app.phase2-seed.minimum.execute=true",
            "--app.phase2-seed.bulk.execute=true"
        };

        assertThatThrownBy(() -> Phase2SeedProcessRunner.validateExecutionFlags(args))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("동시에 실행");
    }

    @Test
    @DisplayName("대량 시드 실행 플래그도 DevTools 재시작을 끈다")
    void disablesDevToolsForBulkSeed() {
        String previous = System.getProperty(DEVTOOLS_RESTART_PROPERTY);
        try {
            System.clearProperty(DEVTOOLS_RESTART_PROPERTY);
            boolean disabled = Phase2SeedProcessRunner.disableDevToolsRestartForSeed(
                    new String[] {"--spring.profiles.active=phase2-seed", "--app.phase2-seed.bulk.execute=true"});
            assertThat(disabled).isTrue();
        } finally {
            restoreSystemProperty(previous);
        }
    }

    private void restoreSystemProperty(String previous) {
        if (previous == null) {
            System.clearProperty(DEVTOOLS_RESTART_PROPERTY);
        } else {
            System.setProperty(DEVTOOLS_RESTART_PROPERTY, previous);
        }
    }

    private GenericApplicationContext contextWithSeedExecution(boolean profileActive, boolean enabled) {
        GenericApplicationContext context = new GenericApplicationContext();
        if (profileActive) {
            context.getEnvironment().setActiveProfiles("phase2-seed");
        }
        context.getEnvironment()
                .getPropertySources()
                .addFirst(new MapPropertySource(
                        "phase2SeedTest", Map.of("app.phase2-seed.minimum.execute", Boolean.toString(enabled))));
        context.refresh();
        return context;
    }

    private GenericApplicationContext contextWithPh11BaselineExecution(boolean profileActive, boolean enabled) {

        GenericApplicationContext context = new GenericApplicationContext();

        if (profileActive) {
            context.getEnvironment().setActiveProfiles("ph11-baseline");
        }

        context.getEnvironment()
                .getPropertySources()
                .addFirst(new MapPropertySource(
                        "ph11BaselineTest", Map.of("app.ph11-baseline.execute", Boolean.toString(enabled))));

        context.refresh();

        return context;
    }
}
