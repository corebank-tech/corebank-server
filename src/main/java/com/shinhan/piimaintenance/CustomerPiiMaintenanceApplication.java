package com.shinhan.piimaintenance;

import com.shinhan.corebank.customer.adapter.out.persistence.CustomerPiiBackfillService;
import com.shinhan.corebank.customer.adapter.out.security.CustomerPiiCryptoConfiguration;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.data.jpa.autoconfigure.DataJpaRepositoriesAutoConfiguration;
import org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration;
import org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration;
import org.springframework.context.annotation.Import;

@SpringBootConfiguration
@EnableAutoConfiguration(
        exclude = {
            FlywayAutoConfiguration.class,
            DataJpaRepositoriesAutoConfiguration.class,
            HibernateJpaAutoConfiguration.class
        })
@Import({
    CustomerPiiCryptoConfiguration.class,
    CustomerPiiBackfillService.class,
    CustomerPiiVerificationService.class,
    CustomerPiiMaintenanceRunner.class
})
public class CustomerPiiMaintenanceApplication {

    public static void main(String[] args) {
        // 일회성 DB 작업에서는 DevTools 재시작을 비활성화한다.
        System.setProperty("spring.devtools.restart.enabled", "false");

        try (var context = new SpringApplicationBuilder(CustomerPiiMaintenanceApplication.class)
                .web(WebApplicationType.NONE)
                .properties("spring.flyway.enabled=false", "spring.main.banner-mode=off")
                .run(args)) {

            // ApplicationRunner가 완료되면
            // 컨텍스트를 닫고 정상 종료한다.
        }
    }
}
