package com.shinhan.corebank.batch.adapter.in.scheduler;

import static org.mockito.Mockito.verify;

import com.shinhan.corebank.batch.application.port.in.CobRunnerUseCase;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CobSchedulerTest {

    @Mock
    CobRunnerUseCase cobRunnerUseCase;

    @Test
    @DisplayName("Asia/Seoul 기준 오늘 날짜로 유스케이스를 호출한다")
    void runCob_delegatesToUseCase_withTodayInSeoul() {
        LocalDate fixedDate = LocalDate.of(2026, 3, 15);
        Clock clock =
                Clock.fixed(fixedDate.atStartOfDay(ZoneId.of("Asia/Seoul")).toInstant(), ZoneId.of("Asia/Seoul"));

        new CobScheduler(cobRunnerUseCase, clock).runCob();

        verify(cobRunnerUseCase).run(fixedDate);
    }
}
