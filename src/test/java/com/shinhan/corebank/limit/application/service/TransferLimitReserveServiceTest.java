package com.shinhan.corebank.limit.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.shinhan.corebank.common.exception.BusinessException;
import com.shinhan.corebank.limit.application.port.out.TransferLimitCommandPort;
import com.shinhan.corebank.limit.domain.TransferLimit;
import com.shinhan.corebank.limit.domain.TransferLimitDailyUsage;
import com.shinhan.corebank.limit.domain.exception.LmtErrorCode;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TransferLimitReserveServiceTest {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final Long CUSTOMER_ID = 1L;
    private static final LocalDate TODAY = LocalDate.of(2026, 8, 21);

    @Mock
    TransferLimitCommandPort transferLimitCommandPort;

    @Test
    @DisplayName("두 한도를 모두 통과하면 이체 금액만큼 당일 사용액에 적립하고 저장한다")
    void checkAndReserve_withinBothLimits_addsAmountToDailyUsage() {
        // given
        givenLimit(1_000_000L, 5_000_000L);
        givenUsage(300_000L);

        // when
        serviceAt(TODAY.atTime(14, 0)).checkAndReserve(CUSTOMER_ID, 200_000L);

        // then
        ArgumentCaptor<TransferLimitDailyUsage> saved = ArgumentCaptor.forClass(TransferLimitDailyUsage.class);
        verify(transferLimitCommandPort).saveUsage(saved.capture());
        assertThat(saved.getValue().getUsedAmount()).isEqualTo(500_000L);
    }

    @Test
    @DisplayName("1회 이체한도를 넘으면 LMT0002를 던지고 당일 사용액 행은 잠그지도 않는다")
    void checkAndReserve_exceedsOneTimeLimit_throwsLmt0002WithoutLockingUsage() {
        // given
        givenLimit(1_000_000L, 5_000_000L);

        // when & then
        assertThatThrownBy(() -> serviceAt(TODAY.atTime(14, 0)).checkAndReserve(CUSTOMER_ID, 1_000_001L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(LmtErrorCode.ONE_TIME_LIMIT_EXCEEDED);

        // 1회 한도에서 걸리는 요청이 사용액 행에 X락을 잡으면 통과할 이체까지 대기시킨다
        verify(transferLimitCommandPort, never()).lockDailyUsage(any(), any());
    }

    @Test
    @DisplayName("당일 누적이 1일 이체한도를 넘으면 LMT0003을 던지고 적립하지 않는다")
    void checkAndReserve_exceedsDailyLimit_throwsLmt0003WithoutSaving() {
        // given - 이미 480만원을 썼고 30만원을 더 보내면 500만원을 넘는다
        givenLimit(1_000_000L, 5_000_000L);
        givenUsage(4_800_000L);

        // when & then
        assertThatThrownBy(() -> serviceAt(TODAY.atTime(14, 0)).checkAndReserve(CUSTOMER_ID, 300_000L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(LmtErrorCode.DAILY_LIMIT_EXCEEDED);

        verify(transferLimitCommandPort, never()).saveUsage(any());
    }

    @Test
    @DisplayName("당일 누적이 1일 이체한도와 정확히 같아지는 금액은 통과한다")
    void checkAndReserve_reachesDailyLimitExactly_succeeds() {
        // given
        givenLimit(1_000_000L, 5_000_000L);
        givenUsage(4_800_000L);

        // when
        serviceAt(TODAY.atTime(14, 0)).checkAndReserve(CUSTOMER_ID, 200_000L);

        // then
        ArgumentCaptor<TransferLimitDailyUsage> saved = ArgumentCaptor.forClass(TransferLimitDailyUsage.class);
        verify(transferLimitCommandPort).saveUsage(saved.capture());
        assertThat(saved.getValue().getUsedAmount()).isEqualTo(5_000_000L);
    }

    @Test
    @DisplayName("한도 행이 없는 고객은 정책 기본값 1회 100만원으로 검사한다")
    void checkAndReserve_limitRowMissing_checksAgainstPolicyDefault() {
        // given - 가입 시 기본값 부여(REQ-TRSF-029)가 연결되기 전이라 행이 없는 고객이 있다
        when(transferLimitCommandPort.findForShareByCustomerId(CUSTOMER_ID)).thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> serviceAt(TODAY.atTime(14, 0)).checkAndReserve(CUSTOMER_ID, 1_000_001L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(LmtErrorCode.ONE_TIME_LIMIT_EXCEEDED);
    }

    @Test
    @DisplayName("KST 자정 직후 이체는 전날이 아니라 KST 오늘 일자의 사용액 행을 잠근다")
    void checkAndReserve_justAfterKstMidnight_locksUsageByKstDate() {
        // given
        givenLimit(1_000_000L, 5_000_000L);
        givenUsage(0L);

        // when
        serviceAt(TODAY.atTime(0, 30)).checkAndReserve(CUSTOMER_ID, 100_000L);

        // then
        verify(transferLimitCommandPort).lockDailyUsage(CUSTOMER_ID, TODAY);
    }

    private void givenLimit(long oneTimeLimit, long dailyLimit) {
        when(transferLimitCommandPort.findForShareByCustomerId(CUSTOMER_ID))
                .thenReturn(Optional.of(TransferLimit.restore(CUSTOMER_ID, oneTimeLimit, dailyLimit)));
    }

    private void givenUsage(long usedAmount) {
        when(transferLimitCommandPort.lockDailyUsage(anyLong(), any()))
                .thenReturn(TransferLimitDailyUsage.restore(CUSTOMER_ID, TODAY, usedAmount));
    }

    /** 운영 Clock 이 KST 라서(REQ-NFR-018) 테스트도 같은 시간대로 고정한다. */
    private TransferLimitReserveService serviceAt(LocalDateTime kstNow) {
        Clock clock = Clock.fixed(kstNow.atZone(SEOUL).toInstant(), SEOUL);
        return new TransferLimitReserveService(transferLimitCommandPort, clock);
    }
}
