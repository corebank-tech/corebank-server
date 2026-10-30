package com.shinhan.corebank.business.adapter.in.scheduler;

import com.shinhan.corebank.business.application.port.in.BusinessDateCatchUpUseCase;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

// 자정 스케줄은 PH-43 COB 도입 시 제거하고, 기동 시 맞추기는 남긴다 (#471)
@Component
@Profile("!phase2-seed")
@RequiredArgsConstructor
public class BusinessDateCatchUpScheduler {
    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private final BusinessDateCatchUpUseCase businessDateCatchUpUseCase;
    private final Clock clock;

    // 초기값(과거)이나 서버가 모두 꺼져 있던 동안 밀린 영업일을 오늘로 따라잡는다
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
