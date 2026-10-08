package com.shinhan.corebank.common.init;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@Profile("phase2-seed")
@ConditionalOnProperty(name = "app.phase2-seed.minimum.execute", havingValue = "true")
public class Phase2MinimumSeedLoader implements ApplicationRunner {

    private final Phase2MinimumSeedService seedService;

    public Phase2MinimumSeedLoader(Phase2MinimumSeedService seedService) {
        this.seedService = seedService;
    }

    @Override
    public void run(ApplicationArguments args) {
        // 전용 프로필과 일회성 실행 플래그가 모두 있을 때만 시드를 적재한다.
        Phase2MinimumSeedReport report = seedService.seed(Phase2MinimumSeedSpec.production());
        log.info(
                "PH-60 minimum seed ready: customers={}, accounts={}, transfers={}, ledgerEntries={}, autoTransfers={}, elapsed={}ms",
                report.customers(),
                report.accounts(),
                report.transfers(),
                report.ledgerEntries(),
                report.autoTransfers(),
                report.elapsed().toMillis());
    }
}
