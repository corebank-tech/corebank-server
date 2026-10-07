package com.shinhan.corebank.common.init;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class Phase2BulkSeedLoaderTest {

    @Test
    @DisplayName("대량 시드 로더는 고정 운영 규격을 서비스에 전달한다")
    void loadsProductionSeed() {
        RecordingBulkSeedService service = new RecordingBulkSeedService();
        Phase2BulkSeedLoader loader = new Phase2BulkSeedLoader(service);

        loader.run(null);

        assertThat(service.received).isEqualTo(Phase2BulkSeedSpec.production());
    }

    private static class RecordingBulkSeedService extends Phase2BulkSeedService {

        private Phase2BulkSeedSpec received;

        RecordingBulkSeedService() {
            super(null, null, java.time.Clock.systemUTC());
        }

        @Override
        public Phase2BulkSeedReport seed(Phase2BulkSeedSpec spec) {
            received = spec;
            return new Phase2BulkSeedReport(
                    50_000, 150_000, 3_000_000, 6_000_000, 3_000_000, 6_000_000, 10_000, 10_000, Duration.ZERO);
        }
    }
}
