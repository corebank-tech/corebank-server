package com.shinhan.corebank;

import java.util.Arrays;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.Profiles;

final class Phase2SeedProcessRunner {

    private static final String ACTIVE_PROFILES_ARGUMENT = "--spring.profiles.active=";
    private static final String PHASE2_SEED_PROFILE = "phase2-seed";
    private static final String MINIMUM_EXECUTION_FLAG = "--app.phase2-seed.minimum.execute=true";
    private static final String BULK_EXECUTION_FLAG = "--app.phase2-seed.bulk.execute=true";
    private static final String PH11_BASELINE_PROFILE = "ph11-baseline";
    private static final String PH11_BASELINE_EXECUTION_FLAG = "--app.ph11-baseline.execute=true";

    private Phase2SeedProcessRunner() {}

    static void run(Class<?> primarySource, String[] args) {
        validateExecutionFlags(args);
        disableDevToolsRestartForSeed(args);
        ConfigurableApplicationContext context = SpringApplication.run(primarySource, args);
        closeCompletedSeedProcess(context);
    }

    static boolean disableDevToolsRestartForSeed(String[] args) {
        boolean seedProfileActive = hasProfile(args, PHASE2_SEED_PROFILE);
        boolean ph11BaselineProfileActive = hasProfile(args, PH11_BASELINE_PROFILE);
        boolean seedExecutionEnabled = hasSeedExecutionFlag(args);
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
        boolean minimumEnabled =
                context.getEnvironment().getProperty("app.phase2-seed.minimum.execute", Boolean.class, false);
        boolean bulkEnabled =
                context.getEnvironment().getProperty("app.phase2-seed.bulk.execute", Boolean.class, false);
        validateExecutionFlags(minimumEnabled, bulkEnabled);

        boolean ph11BaselineProfileActive =
                context.getEnvironment().acceptsProfiles(Profiles.of(PH11_BASELINE_PROFILE));
        boolean ph11BaselineExecutionEnabled =
                context.getEnvironment().getProperty("app.ph11-baseline.execute", Boolean.class, false);

        boolean oneShotProcessCompleted = (phase2SeedProfileActive && (minimumEnabled || bulkEnabled))
                || (ph11BaselineProfileActive && ph11BaselineExecutionEnabled);
        if (oneShotProcessCompleted) {
            context.close();
            return true;
        }
        return false;
    }

    static void validateExecutionFlags(String[] args) {
        validateExecutionFlags(
                Arrays.asList(args).contains(MINIMUM_EXECUTION_FLAG),
                Arrays.asList(args).contains(BULK_EXECUTION_FLAG));
    }

    private static boolean hasProfile(String[] args, String expectedProfile) {
        return Arrays.stream(args)
                .filter(arg -> arg.startsWith(ACTIVE_PROFILES_ARGUMENT))
                .map(arg -> arg.substring(ACTIVE_PROFILES_ARGUMENT.length()))
                .flatMap(profiles -> Arrays.stream(profiles.split(",")))
                .anyMatch(expectedProfile::equals);
    }

    private static boolean hasSeedExecutionFlag(String[] args) {
        return Arrays.asList(args).contains(MINIMUM_EXECUTION_FLAG)
                || Arrays.asList(args).contains(BULK_EXECUTION_FLAG);
    }

    private static void validateExecutionFlags(boolean minimumEnabled, boolean bulkEnabled) {
        if (minimumEnabled && bulkEnabled) {
            // 서로 다른 전용 대역을 한 프로세스에서 동시에 변경하지 못하게 막는다.
            throw new IllegalArgumentException("PH-60 최소 시드와 PH-60b 대량 시드는 동시에 실행할 수 없습니다.");
        }
    }
}
