package com.shinhan.corebank;

import java.util.Arrays;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.Profiles;

final class Phase2SeedProcessRunner {

    private static final String ACTIVE_PROFILES_ARGUMENT = "--spring.profiles.active=";
    private static final String PHASE2_SEED_PROFILE = "phase2-seed";
    private static final String EXECUTION_FLAG = "--app.phase2-seed.execute=true";
    private static final String PH11_BASELINE_PROFILE = "ph11-baseline";
    private static final String PH11_BASELINE_EXECUTION_FLAG = "--app.ph11-baseline.execute=true";

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

        boolean ph11BaselineProfileActive = Arrays.stream(args)
                .filter(arg -> arg.startsWith(ACTIVE_PROFILES_ARGUMENT))
                .map(arg -> arg.substring(ACTIVE_PROFILES_ARGUMENT.length()))
                .flatMap(profiles -> Arrays.stream(profiles.split(",")))
                .anyMatch(PH11_BASELINE_PROFILE::equals);

        boolean seedExecutionEnabled = Arrays.asList(args).contains(EXECUTION_FLAG);
        boolean ph11BaselineExecutionEnabled = Arrays.asList(args).contains(PH11_BASELINE_EXECUTION_FLAG);

        if ((seedProfileActive && seedExecutionEnabled)
                || (ph11BaselineProfileActive && ph11BaselineExecutionEnabled)) {
            // 일회성 프로세스가 컨텍스트를 닫을 때 DevTools 재시작으로 종료 코드가 바뀌지 않게 한다.
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

        boolean ph11BaselineProfileActive =
                context.getEnvironment().acceptsProfiles(Profiles.of(PH11_BASELINE_PROFILE));
        boolean ph11BaselineExecutionEnabled =
                context.getEnvironment().getProperty("app.ph11-baseline.execute", Boolean.class, false);

        boolean oneShotProcessCompleted = (phase2SeedProfileActive && seedExecutionEnabled)
                || (ph11BaselineProfileActive && ph11BaselineExecutionEnabled);

        if (oneShotProcessCompleted) {
            context.close();
            return true;
        }

        return false;
    }
}
