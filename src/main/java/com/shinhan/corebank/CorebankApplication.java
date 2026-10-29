package com.shinhan.corebank;

import com.shinhan.piimaintenance.CustomerPiiMaintenanceApplication;
import java.util.Arrays;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class CorebankApplication {

    public static void main(String[] args) {
        boolean piiMaintenance = Arrays.stream(args)
                .filter(arg -> arg.startsWith("--spring.profiles.active="))
                .map(arg -> arg.substring("--spring.profiles.active=".length()))
                .flatMap(value -> Arrays.stream(value.split(",")))
                .anyMatch("pii-maintenance"::equals);

        if (piiMaintenance) {
            CustomerPiiMaintenanceApplication.main(args);
            return;
        }

        Phase2SeedProcessRunner.run(CorebankApplication.class, args);
    }
}
