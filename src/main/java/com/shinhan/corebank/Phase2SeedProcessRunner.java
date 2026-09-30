package com.shinhan.corebank;

import java.util.Arrays;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.Profiles;

final class Phase2SeedProcessRunner {

    private static final String ACTIVE_PROFILES_ARGUMENT = "--spring.profiles.active=";
    private static final String PHASE2_SEED_PROFILE = "phase2-seed";
    private static final String EXECUTION_FLAG = "--app.phase2-seed.execute=true";

    private Phase2SeedProcessRunner() {}

    static void run(Class<?> primarySource, String[] args) {
        disableDevToolsRestartForSeed(args);
        ConfigurableApplicationContext context = SpringApplication.run(primarySource, args);
        closeCompletedSeedProcess(context);
    }

    static boolean disableDevToolsRestartForSeed(String[] args) {
        boolean seedProfileActive = Arrays.stream(args)
                .filter(arg -> arg.startsWith(ACTIVE_PROFILES_ARGUMENT))
                .map(arg -> arg.substring(ACTIVE_PROFILES_ARGUMENT.length()))
                .flatMap(profiles -> Arrays.stream(profiles.split(",")))
                .anyMatch(PHASE2_SEED_PROFILE::equals);
        boolean seedExecutionEnabled = Arrays.asList(args).contains(EXECUTION_FLAG);
        if (seedProfileActive && seedExecutionEnabled) {
            // DevTools가 만든 restartedMain에서 컨텍스트를 닫으면 bootRun이 종료 코드 1로 끝날 수 있다.
            System.setProperty("spring.devtools.restart.enabled", "false");
            return true;
        }
        return false;
    }

    static boolean closeCompletedSeedProcess(ConfigurableApplicationContext context) {
        // 모든 시드 적재와 시작 이벤트가 끝난 뒤 일회성 프로세스를 정상 종료한다.
        boolean phase2SeedProfileActive = context.getEnvironment().acceptsProfiles(Profiles.of(PHASE2_SEED_PROFILE));
        boolean seedExecutionEnabled =
                context.getEnvironment().getProperty("app.phase2-seed.execute", Boolean.class, false);
        if (phase2SeedProfileActive && seedExecutionEnabled) {
            context.close();
            return true;
        }
        return false;
    }
}
