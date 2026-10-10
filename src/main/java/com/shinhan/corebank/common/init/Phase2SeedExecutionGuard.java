package com.shinhan.corebank.common.init;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.Ordered;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Component
@Profile("phase2-seed")
public class Phase2SeedExecutionGuard implements ApplicationRunner, Ordered {

    private final Environment environment;

    public Phase2SeedExecutionGuard(Environment environment) {
        this.environment = environment;
    }

    @Override
    public int getOrder() {
        // 데이터 변경 리스너보다 먼저 실행해 잘못된 플래그 조합을 차단한다.
        return Ordered.HIGHEST_PRECEDENCE;
    }

    @Override
    public void run(ApplicationArguments args) {
        validate();
    }

    void validate() {
        boolean minimum = environment.getProperty("app.phase2-seed.minimum.execute", Boolean.class, false);
        boolean bulk = environment.getProperty("app.phase2-seed.bulk.execute", Boolean.class, false);
        if (minimum && bulk) {
            throw new IllegalArgumentException("PH-60 최소 시드와 PH-60b 대량 시드는 동시에 실행할 수 없습니다.");
        }
    }
}
