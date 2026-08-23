package com.shinhan.corebank.limit.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.shinhan.corebank.common.exception.BusinessException;
import com.shinhan.corebank.limit.application.port.in.dto.TransferLimitCommand;
import com.shinhan.corebank.limit.application.port.in.dto.TransferLimitResult;
import com.shinhan.corebank.limit.application.port.out.AuthTokenVerificationPort;
import com.shinhan.corebank.limit.application.port.out.TransferLimitCommandPort;
import com.shinhan.corebank.limit.application.port.out.TransferLimitHistoryPort;
import com.shinhan.corebank.limit.application.port.out.TransferLimitQueryPort;
import com.shinhan.corebank.limit.domain.exception.LmtErrorCode;
import com.shinhan.corebank.otp.domain.exception.OtpErrorCode;
import com.shinhan.corebank.limit.domain.TransferLimit;
import com.shinhan.corebank.limit.domain.TransferLimitDailyUsage;
import com.shinhan.corebank.limit.domain.TransferLimitHistory;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TransferLimitCommandServiceTest {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final LocalDate TODAY = LocalDate.of(2026, 8, 21);
    private static final Long CUSTOMER_ID = 1L;

    @Mock
    TransferLimitCommandPort transferLimitCommandPort;
    @Mock
    TransferLimitQueryPort transferLimitQueryPort;
    @Mock
    TransferLimitHistoryPort transferLimitHistoryPort;
    @Mock
    AuthTokenVerificationPort authTokenVerificationPort;

    @Test
    @DisplayName("한도를 변경하면 새 값으로 저장하고 변경된 한도와 당일 사용 현황을 함께 반환한다")
    void update_validCommand_savesAndReturnsNewLimits() {
        // given
        when(transferLimitCommandPort.findForUpdateByCustomerId(CUSTOMER_ID))
                .thenReturn(Optional.of(TransferLimit.restore(CUSTOMER_ID, 1_000_000L, 5_000_000L)));
        when(transferLimitCommandPort.save(any()))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(transferLimitQueryPort.findUsage(CUSTOMER_ID, TODAY))
                .thenReturn(Optional.of(TransferLimitDailyUsage.restore(CUSTOMER_ID, TODAY, 300_000L)));

        // when
        TransferLimitResult result = service().update(CUSTOMER_ID, command(3_000_000L, 10_000_000L));

        // then
        assertThat(result.oneTimeLimit()).isEqualTo(3_000_000L);
        assertThat(result.dailyLimit()).isEqualTo(10_000_000L);
        assertThat(result.dailyUsedAmount()).isEqualTo(300_000L);
        assertThat(result.dailyRemainingAmount()).isEqualTo(9_700_000L);
    }

    @Test
    @DisplayName("변경 직전 값을 이력으로 남긴다")
    void update_savesHistoryWithValuesBeforeChange() {
        // given
        when(transferLimitCommandPort.findForUpdateByCustomerId(CUSTOMER_ID))
                .thenReturn(Optional.of(TransferLimit.restore(CUSTOMER_ID, 1_000_000L, 5_000_000L)));
        when(transferLimitCommandPort.save(any()))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(transferLimitQueryPort.findUsage(CUSTOMER_ID, TODAY)).thenReturn(Optional.empty());

        // when
        service().update(CUSTOMER_ID, command(3_000_000L, 10_000_000L));

        // then
        ArgumentCaptor<TransferLimitHistory> captor = ArgumentCaptor.forClass(TransferLimitHistory.class);
        verify(transferLimitHistoryPort).save(captor.capture());
        assertThat(captor.getValue().getBeforeOneTimeLimit()).isEqualTo(1_000_000L);
        assertThat(captor.getValue().getBeforeDailyLimit()).isEqualTo(5_000_000L);
    }

    @Test
    @DisplayName("1회 한도가 1일 한도보다 크면 OTP를 소모하기 전에 LMT0004로 거부한다")
    void update_oneTimeOverDaily_rejectsBeforeConsumingOtp() {
        // when & then
        assertThatThrownBy(() -> service().update(CUSTOMER_ID, command(10_000_000L, 5_000_000L)))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(LmtErrorCode.ONE_TIME_LIMIT_OVER_DAILY));

        // 입력 실수로 토큰이 소모되면 사용자가 OTP를 다시 받아야 한다. 소모 전에 걸러야 한다.
        verify(authTokenVerificationPort, never())
                .verifyAndConsumeOtp(any(), any(), anyLong(), anyLong());
        // 인증 전에 X락을 잡으면 실패할 요청이 남의 이체를 대기시킨다.
        verify(transferLimitCommandPort, never()).findForUpdateByCustomerId(any());
        verify(transferLimitCommandPort, never()).save(any());
    }

    @Test
    @DisplayName("한도 행이 없는 고객의 변경 요청은 LMT9001로 거부하고 이력도 남기지 않는다")
    void update_limitRowMissing_rejectsWithLmt9001() {
        // given - 가입 연계와 백필이 보장하므로 나올 수 없는 상태다. 나오면 데이터 결함이다
        when(transferLimitCommandPort.findForUpdateByCustomerId(CUSTOMER_ID)).thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> service().update(CUSTOMER_ID, command(3_000_000L, 10_000_000L)))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(LmtErrorCode.TRANSFER_LIMIT_NOT_FOUND));

        // 없는 한도를 바꿨다는 이력이 남으면 감사 이력이 거짓이 된다
        verify(transferLimitHistoryPort, never()).save(any());
        verify(transferLimitCommandPort, never()).save(any());
    }

    @Test
    @DisplayName("한도를 읽기 전에 계좌비밀번호와 OTP 토큰을 모두 검증한다")
    void update_verifiesBothAuthTokensBeforeReadingLimit() {
        // given
        when(transferLimitCommandPort.findForUpdateByCustomerId(CUSTOMER_ID))
                .thenReturn(Optional.of(TransferLimit.restore(CUSTOMER_ID, 1_000_000L, 5_000_000L)));
        when(transferLimitCommandPort.save(any()))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(transferLimitQueryPort.findUsage(CUSTOMER_ID, TODAY)).thenReturn(Optional.empty());

        // when
        service().update(CUSTOMER_ID, command(3_000_000L, 10_000_000L));

        // then - 계좌가 아니라 고객 기준으로 검증하고, OTP 에는 바꾸려는 한도를 함께 넘겨 거래내용을 대조하게 한다.
        // 순서까지 보는 이유는 인증 전에 X락을 잡으면 실패할 요청이 같은 고객의 이체를 대기시키기 때문이다.
        InOrder inOrder = inOrder(authTokenVerificationPort, transferLimitCommandPort);
        inOrder.verify(authTokenVerificationPort).verifyAccountPassword("ACC_PWD_TOKEN", CUSTOMER_ID);
        inOrder.verify(authTokenVerificationPort)
                .verifyAndConsumeOtp("OTP_AUTH_TOKEN", CUSTOMER_ID, 3_000_000L, 10_000_000L);
        inOrder.verify(transferLimitCommandPort).findForUpdateByCustomerId(CUSTOMER_ID);
    }

    @Test
    @DisplayName("OTP 검증이 실패하면 한도를 읽지도 저장하지도 않는다")
    void update_otpVerificationFails_doesNotTouchLimit() {
        // given - 인증한 거래내용과 요청 한도가 다르면 otp 모듈이 OTP0102 를 던진다
        doThrow(new BusinessException(OtpErrorCode.TRANSACTION_MISMATCH))
                .when(authTokenVerificationPort)
                .verifyAndConsumeOtp("OTP_AUTH_TOKEN", CUSTOMER_ID, 3_000_000L, 10_000_000L);

        // when & then
        assertThatThrownBy(() -> service().update(CUSTOMER_ID, command(3_000_000L, 10_000_000L)))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(OtpErrorCode.TRANSACTION_MISMATCH));

        verify(transferLimitCommandPort, never()).findForUpdateByCustomerId(any());
        verify(transferLimitCommandPort, never()).save(any());
    }

    private TransferLimitCommand command(long oneTimeLimit, long dailyLimit) {
        return TransferLimitCommand.builder()
                .oneTimeLimit(oneTimeLimit)
                .dailyLimit(dailyLimit)
                .accountPasswordAuthToken("ACC_PWD_TOKEN")
                .otpAuthToken("OTP_AUTH_TOKEN")
                .build();
    }

    /** 운영 Clock 이 KST 라서(REQ-NFR-018) 테스트도 같은 시간대로 고정한다. */
    private TransferLimitCommandService service() {
        Clock clock = Clock.fixed(TODAY.atTime(14, 0).atZone(SEOUL).toInstant(), SEOUL);
        return new TransferLimitCommandService(
                transferLimitCommandPort, transferLimitQueryPort, transferLimitHistoryPort,
                authTokenVerificationPort, clock);
    }
}
