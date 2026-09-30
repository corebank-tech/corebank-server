package com.shinhan.corebank;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.Profiles;

@SpringBootApplication
public class CorebankApplication {

    public static void main(String[] args) {
        ConfigurableApplicationContext context = SpringApplication.run(CorebankApplication.class, args);
        closeCompletedSeedProcess(context);
    }

    static boolean closeCompletedSeedProcess(ConfigurableApplicationContext context) {
        // 모든 시드 적재와 시작 이벤트가 끝난 뒤 일회성 프로세스를 정상 종료한다.
        boolean phase2SeedProfileActive = context.getEnvironment().acceptsProfiles(Profiles.of("phase2-seed"));
        boolean seedExecutionEnabled =
                context.getEnvironment().getProperty("app.phase2-seed.execute", Boolean.class, false);
        if (phase2SeedProfileActive && seedExecutionEnabled) {
            context.close();
            return true;
        }
        return false;
    }
}
