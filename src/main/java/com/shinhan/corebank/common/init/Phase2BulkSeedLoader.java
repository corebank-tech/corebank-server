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
@ConditionalOnProperty(name = "app.phase2-seed.bulk.execute", havingValue = "true")
public class Phase2BulkSeedLoader implements ApplicationRunner {

    private final Phase2BulkSeedService seedService;

    public Phase2BulkSeedLoader(Phase2BulkSeedService seedService) {
        this.seedService = seedService;
    }

    @Override
    public void run(ApplicationArguments args) {
        // 명시적인 대량 시드 플래그에서만 운영 규격을 적재한다.
        Phase2BulkSeedReport report = seedService.seed(Phase2BulkSeedSpec.production());
        log.info(
                "PH-60b bulk seed ready: customers={}, accounts={}, transactions={}, ledgerEntries={}, vouchers={}, journalEntries={}, autoTransfers={}, scheduledTransfers={}, elapsed={}ms",
                report.customers(),
                report.accounts(),
                report.transactions(),
                report.ledgerEntries(),
                report.vouchers(),
                report.journalEntries(),
                report.autoTransfers(),
                report.scheduledTransfers(),
                report.elapsed().toMillis());
    }
}
