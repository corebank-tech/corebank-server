package com.shinhan.corebank.business.adapter.in.scheduler;

import com.shinhan.corebank.business.application.port.in.BusinessDateCatchUpUseCase;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

// 임시 — PH-43 COB 가 영업일 전환 스텝을 가지면 제거한다 (#471)
@Component
@RequiredArgsConstructor
public class BusinessDateCatchUpScheduler {
    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private final BusinessDateCatchUpUseCase businessDateCatchUpUseCase;
    private final Clock clock;

    // 초기값은 환경마다 Flyway 가 도는 날 들어가므로 기동 시에도 맞춘다
    @EventListener(ApplicationReadyEvent.class)
    public void catchUpOnStartup() {
        catchUp();
    }

    // 자동·예약이체 배치(00:10)보다 먼저 돈다
    @Scheduled(cron = "0 0 0 * * *", zone = "Asia/Seoul")
    public void catchUp() {
        businessDateCatchUpUseCase.catchUpTo(LocalDate.now(clock.withZone(SEOUL)));
    }
}
