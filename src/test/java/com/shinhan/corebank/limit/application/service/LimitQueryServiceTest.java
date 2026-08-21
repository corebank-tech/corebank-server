package com.shinhan.corebank.limit.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.shinhan.corebank.limit.application.port.in.dto.LimitResult;
import com.shinhan.corebank.limit.application.port.out.TransferLimitQueryPort;
import com.shinhan.corebank.limit.domain.TransferLimit;
import com.shinhan.corebank.limit.domain.TransferLimitDailyUsage;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LimitQueryServiceTest {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final Long CUSTOMER_ID = 1L;
    private static final LocalDate TODAY = LocalDate.of(2026, 8, 19);

    @Mock
    TransferLimitQueryPort transferLimitQueryPort;

    @Test
    @DisplayName("한도와 당일 사용액이 모두 있으면 1회·1일 한도와 사용액·잔여액을 함께 반환한다")
    void get_limitAndUsageExist_returnsAllFourAmounts() {
        // given
        when(transferLimitQueryPort.findByCustomerId(CUSTOMER_ID))
                .thenReturn(Optional.of(TransferLimit.restore(CUSTOMER_ID, 2_000_000L, 8_000_000L)));
        when(transferLimitQueryPort.findUsage(CUSTOMER_ID, TODAY))
                .thenReturn(Optional.of(TransferLimitDailyUsage.restore(CUSTOMER_ID, TODAY, 3_000_000L)));

        // when
        LimitResult result = serviceAt(TODAY.atTime(14, 0)).get(CUSTOMER_ID);

        // then
        assertThat(result.oneTimeLimit()).isEqualTo(2_000_000L);
        assertThat(result.dailyLimit()).isEqualTo(8_000_000L);
        assertThat(result.dailyUsedAmount()).isEqualTo(3_000_000L);
        assertThat(result.dailyRemainingAmount()).isEqualTo(5_000_000L);
    }

    @Test
    @DisplayName("한도 행이 없는 고객은 정책 기본값 1회 100만원·1일 500만원으로 응답한다")
    void get_limitRowMissing_returnsPolicyDefaults() {
        // given - 가입 시 기본값 부여(REQ-TRSF-029)가 연결되기 전이라 행이 없는 고객이 있다
        when(transferLimitQueryPort.findByCustomerId(CUSTOMER_ID)).thenReturn(Optional.empty());
        when(transferLimitQueryPort.findUsage(CUSTOMER_ID, TODAY)).thenReturn(Optional.empty());

        // when
        LimitResult result = serviceAt(TODAY.atTime(14, 0)).get(CUSTOMER_ID);

        // then
        assertThat(result.oneTimeLimit()).isEqualTo(1_000_000L);
        assertThat(result.dailyLimit()).isEqualTo(5_000_000L);
    }

    @Test
    @DisplayName("당일 사용액 행이 없으면(그날 첫 조회) 사용액은 0이고 잔여액은 1일 한도 전액이다")
    void get_usageRowMissing_returnsZeroUsedAndFullRemaining() {
        // given
        when(transferLimitQueryPort.findByCustomerId(CUSTOMER_ID))
                .thenReturn(Optional.of(TransferLimit.restore(CUSTOMER_ID, 1_000_000L, 5_000_000L)));
        when(transferLimitQueryPort.findUsage(CUSTOMER_ID, TODAY)).thenReturn(Optional.empty());

        // when
        LimitResult result = serviceAt(TODAY.atTime(14, 0)).get(CUSTOMER_ID);

        // then
        assertThat(result.dailyUsedAmount()).isZero();
        assertThat(result.dailyRemainingAmount()).isEqualTo(5_000_000L);
    }

    @Test
    @DisplayName("사용액이 1일 한도와 같아지면 잔여액은 0이다")
    void get_usageEqualsDailyLimit_returnsZeroRemaining() {
        // given
        when(transferLimitQueryPort.findByCustomerId(CUSTOMER_ID))
                .thenReturn(Optional.of(TransferLimit.restore(CUSTOMER_ID, 1_000_000L, 5_000_000L)));
        when(transferLimitQueryPort.findUsage(CUSTOMER_ID, TODAY))
                .thenReturn(Optional.of(TransferLimitDailyUsage.restore(CUSTOMER_ID, TODAY, 5_000_000L)));

        // when
        LimitResult result = serviceAt(TODAY.atTime(14, 0)).get(CUSTOMER_ID);

        // then
        assertThat(result.dailyRemainingAmount()).isZero();
    }

    @Test
    @DisplayName("1일 한도가 줄어 사용액이 한도를 넘긴 상태여도 잔여액은 음수가 되지 않는다")
    void get_usageExceedsDailyLimit_remainingIsClampedToZero() {
        // given - 한도 변경으로 이미 쓴 금액이 새 한도를 넘길 수 있다
        when(transferLimitQueryPort.findByCustomerId(CUSTOMER_ID))
                .thenReturn(Optional.of(TransferLimit.restore(CUSTOMER_ID, 1_000_000L, 3_000_000L)));
        when(transferLimitQueryPort.findUsage(CUSTOMER_ID, TODAY))
                .thenReturn(Optional.of(TransferLimitDailyUsage.restore(CUSTOMER_ID, TODAY, 5_000_000L)));

        // when
        LimitResult result = serviceAt(TODAY.atTime(14, 0)).get(CUSTOMER_ID);

        // then
        assertThat(result.dailyRemainingAmount()).isZero();
    }

    @Test
    @DisplayName("KST 자정 직후에도 당일 사용액을 UTC 전날이 아닌 KST 오늘 일자로 조회한다")
    void get_justAfterKstMidnight_looksUpUsageByKstDate() {
        // given - KST 08-19 00:30 은 UTC 로는 08-18 15:30 이다
        when(transferLimitQueryPort.findByCustomerId(CUSTOMER_ID))
                .thenReturn(Optional.of(TransferLimit.restore(CUSTOMER_ID, 1_000_000L, 5_000_000L)));
        when(transferLimitQueryPort.findUsage(CUSTOMER_ID, TODAY))
                .thenReturn(Optional.of(TransferLimitDailyUsage.restore(CUSTOMER_ID, TODAY, 700_000L)));

        // when
        LimitResult result = serviceAt(TODAY.atTime(0, 30)).get(CUSTOMER_ID);

        // then
        assertThat(result.dailyUsedAmount()).isEqualTo(700_000L);
    }

    @Test
    @DisplayName("1회 이체한도만 조회하면 한도 행의 1회 한도를 그대로 돌려준다")
    void findOneTimeLimit_limitExists_returnsStoredOneTimeLimit() {
        // given
        when(transferLimitQueryPort.findByCustomerId(CUSTOMER_ID))
                .thenReturn(Optional.of(TransferLimit.restore(CUSTOMER_ID, 2_000_000L, 8_000_000L)));

        // when
        long oneTimeLimit = serviceAt(TODAY.atTime(14, 0)).findOneTimeLimit(CUSTOMER_ID);

        // then
        assertThat(oneTimeLimit).isEqualTo(2_000_000L);
    }

    @Test
    @DisplayName("한도 행이 없는 고객의 1회 이체한도는 정책 기본값 100만원이다")
    void findOneTimeLimit_limitRowMissing_returnsPolicyDefault() {
        // given - 가입 시 기본값 부여(REQ-TRSF-029)가 연결되기 전이라 행이 없는 고객이 있다
        when(transferLimitQueryPort.findByCustomerId(CUSTOMER_ID)).thenReturn(Optional.empty());

        // when
        long oneTimeLimit = serviceAt(TODAY.atTime(14, 0)).findOneTimeLimit(CUSTOMER_ID);

        // then - 당일 사용액은 등록 검증에 필요 없으므로 조회하지 않는다
        assertThat(oneTimeLimit).isEqualTo(1_000_000L);
    }

    /** 운영 Clock 이 KST 라서(REQ-NFR-018) 테스트도 같은 시간대로 고정한다. */
    private LimitQueryService serviceAt(LocalDateTime kstNow) {
        Clock clock = Clock.fixed(kstNow.atZone(SEOUL).toInstant(), SEOUL);
        return new LimitQueryService(transferLimitQueryPort, clock);
    }
}
