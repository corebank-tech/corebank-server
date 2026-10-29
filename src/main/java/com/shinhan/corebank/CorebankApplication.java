package com.shinhan.corebank;

import com.shinhan.piimaintenance.CustomerPiiMaintenanceApplication;
import java.util.Arrays;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class CorebankApplication {

    public static void main(String[] args) {
        String activeProfiles = Arrays.stream(args)
        .filter(arg -> arg.startsWith("--spring.profiles.active="))
        .map(arg -> arg.substring("--spring.profiles.active=".length()))
        .findFirst()
        .orElseGet(() -> {
            String systemProfiles = System.getProperty("spring.profiles.active");
            return systemProfiles != null
                    ? systemProfiles
                    : System.getenv("SPRING_PROFILES_ACTIVE");
        });

        boolean piiMaintenance = activeProfiles != null
                && Arrays.stream(activeProfiles.split(","))
                        .map(String::trim)
                        .anyMatch("pii-maintenance"::equals);

        if (piiMaintenance) {
            CustomerPiiMaintenanceApplication.main(args);
            return;
        }

        Phase2SeedProcessRunner.run(CorebankApplication.class, args);
    }
}
