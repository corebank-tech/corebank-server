package com.shinhan.corebank.common.init;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class Phase2MinimumSeedLoaderTest {

    @Test
    @DisplayName("phase2-seed 로더는 고정 운영 규격으로 최소 시드를 적재한다")
    void loadsProductionSeed() {
        RecordingSeedService service = new RecordingSeedService();
        Phase2MinimumSeedSpec expected = Phase2MinimumSeedSpec.production();
        Phase2MinimumSeedLoader loader = new Phase2MinimumSeedLoader(service);

        loader.run(null);

        assertThat(service.received).isEqualTo(expected);
    }

    private static class RecordingSeedService extends Phase2MinimumSeedService {

        private Phase2MinimumSeedSpec received;

        RecordingSeedService() {
            super(null);
        }

        @Override
        public Phase2MinimumSeedReport seed(Phase2MinimumSeedSpec spec) {
            received = spec;
            return new Phase2MinimumSeedReport(10_000, 30_000, 500_000, 5_000, 235_000, Duration.ZERO);
        }
    }
}
