package com.shinhan.corebank.scheduledtransfer.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.shinhan.corebank.account.domain.AccountType;
import com.shinhan.corebank.common.audit.AuditEventType;
import com.shinhan.corebank.common.audit.AuditLogService;
import com.shinhan.corebank.common.exception.BusinessException;
import com.shinhan.corebank.common.exception.CommonErrorCode;
import com.shinhan.corebank.scheduledtransfer.application.port.in.ScheduledTransferCancelCommand;
import com.shinhan.corebank.scheduledtransfer.application.port.in.ScheduledTransferRegisterCommand;
import com.shinhan.corebank.scheduledtransfer.application.port.out.AccountStatusPort;
import com.shinhan.corebank.scheduledtransfer.application.port.out.AuthTokenVerificationPort;
import com.shinhan.corebank.scheduledtransfer.application.port.out.ScheduledTransferPersistencePort;
import com.shinhan.corebank.scheduledtransfer.application.port.out.TransferLimitPort;
import com.shinhan.corebank.scheduledtransfer.domain.ScheduledTransfer;
import com.shinhan.corebank.scheduledtransfer.domain.ScheduledTransferStatus;
import com.shinhan.corebank.scheduledtransfer.domain.exception.ScheduledTransferErrorCode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ScheduledTransferCommandServiceTest {

    @Mock
    ScheduledTransferPersistencePort scheduledTransferPersistencePort;

    @Mock
    AuthTokenVerificationPort authTokenVerificationPort;

    @Mock
    AccountStatusPort accountStatusPort;

    @Mock
    TransferLimitPort transferLimitPort;

    @Mock
    AuditLogService auditLogService;

    @Mock
    Clock clock;

    @InjectMocks
    ScheduledTransferCommandService scheduledTransferCommandService;

    // scheduledDate가 clock에서 파생되므로, 호출 전에 stubClock()이 먼저 실행돼 있어야 한다.
    private ScheduledTransferRegisterCommand.ScheduledTransferRegisterCommandBuilder validCommandBuilder() {
        return ScheduledTransferRegisterCommand.builder()
                .customerId(1L)
                .withdrawalAccountId(2L)
                .depositAccountNumber("110987654321")
                .payeeName("홍길동")
                .amount(10_000L)
                .scheduledDate(LocalDate.now(clock).plusDays(10))
                .myPassbookMemo("내메모")
                .recipientPassbookMemo("받는메모")
                .accountPasswordAuthToken("valid-token")
                .otpAuthToken("valid-otp-token")
                .requestIp("127.0.0.1");
    }

    // LocalDate.now(clock)/LocalDateTime.now(clock)이 내부적으로 instant()/getZone()을 호출하므로 이 둘을 스텁한다.
    // zone은 운영 Clock 빈(JpaAuditingConfig)과 동일한 Asia/Seoul이어야 한다. 날짜 픽스처도 반드시 이
    // clock으로 생성해야 한다 — 픽스처가 LocalDate.now()(JVM 기본 zone)를 따로 쓰면, 서비스가 계산하는
    // today와 다른 시간대·시점 기준이 되어 KST 자정 전후나 CI 기본 zone이 다를 때 결과가 어긋날 수 있다.
    // 일부 테스트는 서비스가 clock에 도달하기 전에 반환·예외처리 하므로 lenient로 스텁한다.
    private void stubClock() {
        lenient().when(clock.instant()).thenReturn(Instant.now());
        lenient().when(clock.getZone()).thenReturn(ZoneId.of("Asia/Seoul"));
    }

    @Test
    @DisplayName("모든 검증을 통과하면 등록하고 저장된 결과를 반환한다")
    void register_success() {
        when(accountStatusPort.belongsToCustomer(2L, 1L)).thenReturn(true);
        when(accountStatusPort.isActiveAccount(2L)).thenReturn(true);
        when(accountStatusPort.isWithdrawalRegistered(2L)).thenReturn(true);
        when(accountStatusPort.findAccountTypeByNumber("110987654321")).thenReturn(Optional.of(AccountType.DEMAND_DEPOSIT));
        when(transferLimitPort.findOneTimeLimit(1L)).thenReturn(1_000_000L);
        when(scheduledTransferPersistencePort.existsActiveDuplicate(eq(1L), eq(2L), eq("110987654321"), eq(10_000L), any()))
                .thenReturn(false);
        stubClock();
        when(scheduledTransferPersistencePort.save(any(ScheduledTransfer.class))).thenAnswer(invocation -> {
            ScheduledTransfer arg = invocation.getArgument(0);
            return ScheduledTransfer.reconstitute(
                    100L, arg.getCustomerId(), arg.getWithdrawalAccountId(), arg.getPayeeBankCode(), arg.getPayeeAccountNumber(),
                    arg.getPayeeName(), arg.getAmount(), arg.getScheduledDate(), arg.getMyPassbookMemo(), arg.getRecipientPassbookMemo(),
                    arg.getStatus(), arg.getTransactionNumber(), arg.getRegisteredAt(), arg.getExecutedAt(), arg.getCanceledAt(),
                    arg.getFailureReason());
        });

        ScheduledTransfer result = scheduledTransferCommandService.register(validCommandBuilder().build());

        assertThat(result.getWithdrawalAccountId()).isEqualTo(2L);
        assertThat(result.getAmount()).isEqualTo(10_000L);
        assertThat(result.getStatus()).isEqualTo(ScheduledTransferStatus.WAITING);
        verify(scheduledTransferPersistencePort).save(any(ScheduledTransfer.class));
        verify(authTokenVerificationPort).verify(eq("valid-token"), eq(2L), anyString());
        verify(authTokenVerificationPort).verify(eq("valid-otp-token"), eq(2L), anyString());
        verify(auditLogService).record(eq(1L), isNull(), eq(AuditEventType.SCHEDULED_TRANSFER_INFO_CHANGE),
                eq("127.0.0.1"), eq(true), any());
    }

    @Test
    @DisplayName("예정일자가 유효 범위를 벗어나면 인증 포트를 호출하지 않고 SCD0001을 던진다")
    void register_invalidScheduledDate_doesNotCallAuthTokenVerification() {
        stubClock();
        ScheduledTransferRegisterCommand command = validCommandBuilder()
                .scheduledDate(LocalDate.now(clock).minusDays(5))
                .build();

        assertThatThrownBy(() -> scheduledTransferCommandService.register(command))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(ScheduledTransferErrorCode.INVALID_SCHEDULED_DATE));

        verify(authTokenVerificationPort, never()).verify(any(), any(), any());
    }

    @Test
    @DisplayName("인증 토큰이 유효하지 않으면 그 자리에서 예외가 전파되고 이후 검증은 실행되지 않는다")
    void register_invalidAuthToken_propagatesException() {
        stubClock();
        doThrow(new BusinessException(CommonErrorCode.UNAUTHORIZED))
                .when(authTokenVerificationPort).verify(anyString(), any(), anyString());

        assertThatThrownBy(() -> scheduledTransferCommandService.register(validCommandBuilder().build()))
                .isInstanceOf(BusinessException.class);

        verify(accountStatusPort, never()).belongsToCustomer(any(), any());
    }

    @Test
    @DisplayName("출금계좌가 본인 소유가 아니면 ACCOUNT_NOT_ACCESSIBLE을 던진다")
    void register_accountNotOwnedByCustomer_throwsAccountNotAccessible() {
        stubClock();
        when(accountStatusPort.belongsToCustomer(2L, 1L)).thenReturn(false);

        assertThatThrownBy(() -> scheduledTransferCommandService.register(validCommandBuilder().build()))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(ScheduledTransferErrorCode.ACCOUNT_NOT_ACCESSIBLE));

        verify(accountStatusPort, never()).isActiveAccount(any());
    }

    @Test
    @DisplayName("출금계좌가 비활성 상태면 ACCOUNT_NOT_ACCESSIBLE을 던진다")
    void register_inactiveWithdrawalAccount_throwsAccountNotAccessible() {
        stubClock();
        when(accountStatusPort.belongsToCustomer(2L, 1L)).thenReturn(true);
        when(accountStatusPort.isActiveAccount(2L)).thenReturn(false);

        assertThatThrownBy(() -> scheduledTransferCommandService.register(validCommandBuilder().build()))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(ScheduledTransferErrorCode.ACCOUNT_NOT_ACCESSIBLE));

        verify(scheduledTransferPersistencePort, never()).existsActiveDuplicate(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("출금계좌로 등록되지 않은 계좌면 ACCOUNT_NOT_ACCESSIBLE을 던진다")
    void register_withdrawalAccountNotRegistered_throwsAccountNotAccessible() {
        stubClock();
        when(accountStatusPort.belongsToCustomer(2L, 1L)).thenReturn(true);
        when(accountStatusPort.isActiveAccount(2L)).thenReturn(true);
        when(accountStatusPort.isWithdrawalRegistered(2L)).thenReturn(false);

        assertThatThrownBy(() -> scheduledTransferCommandService.register(validCommandBuilder().build()))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(ScheduledTransferErrorCode.ACCOUNT_NOT_ACCESSIBLE));

        verify(accountStatusPort, never()).findAccountTypeByNumber(any());
    }

    @Test
    @DisplayName("입금계좌가 존재하지 않으면 ACCOUNT_NOT_ACCESSIBLE을 던진다")
    void register_payeeAccountNotFound_throwsAccountNotAccessible() {
        stubClock();
        when(accountStatusPort.belongsToCustomer(2L, 1L)).thenReturn(true);
        when(accountStatusPort.isActiveAccount(2L)).thenReturn(true);
        when(accountStatusPort.isWithdrawalRegistered(2L)).thenReturn(true);
        when(accountStatusPort.findAccountTypeByNumber("110987654321")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> scheduledTransferCommandService.register(validCommandBuilder().build()))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(ScheduledTransferErrorCode.ACCOUNT_NOT_ACCESSIBLE));
    }

    @Test
    @DisplayName("입금계좌 유형이 입출금계좌가 아니면 UNSUPPORTED_DEPOSIT_ACCOUNT_TYPE을 던진다")
    void register_unsupportedDepositAccountType_throws() {
        stubClock();
        when(accountStatusPort.belongsToCustomer(2L, 1L)).thenReturn(true);
        when(accountStatusPort.isActiveAccount(2L)).thenReturn(true);
        when(accountStatusPort.isWithdrawalRegistered(2L)).thenReturn(true);
        when(accountStatusPort.findAccountTypeByNumber("110987654321")).thenReturn(Optional.of(AccountType.TIME_DEPOSIT));

        assertThatThrownBy(() -> scheduledTransferCommandService.register(validCommandBuilder().build()))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(ScheduledTransferErrorCode.UNSUPPORTED_DEPOSIT_ACCOUNT_TYPE));
    }

    @Test
    @DisplayName("1회 이체한도를 초과하면 ONE_TIME_LIMIT_EXCEEDED를 던진다")
    void register_exceedsOneTimeLimit_throws() {
        stubClock();
        when(accountStatusPort.belongsToCustomer(2L, 1L)).thenReturn(true);
        when(accountStatusPort.isActiveAccount(2L)).thenReturn(true);
        when(accountStatusPort.isWithdrawalRegistered(2L)).thenReturn(true);
        when(accountStatusPort.findAccountTypeByNumber("110987654321")).thenReturn(Optional.of(AccountType.DEMAND_DEPOSIT));
        when(transferLimitPort.findOneTimeLimit(1L)).thenReturn(5_000L);

        assertThatThrownBy(() -> scheduledTransferCommandService.register(validCommandBuilder().build()))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(ScheduledTransferErrorCode.ONE_TIME_LIMIT_EXCEEDED));

        verify(scheduledTransferPersistencePort, never()).existsActiveDuplicate(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("동일 조건(출금계좌·입금계좌·예정일·금액)의 대기 상태 예약이체가 이미 있으면 DUPLICATE_REGISTRATION을 던진다")
    void register_duplicateRegistration_throws() {
        stubClock();
        when(accountStatusPort.belongsToCustomer(2L, 1L)).thenReturn(true);
        when(accountStatusPort.isActiveAccount(2L)).thenReturn(true);
        when(accountStatusPort.isWithdrawalRegistered(2L)).thenReturn(true);
        when(accountStatusPort.findAccountTypeByNumber("110987654321")).thenReturn(Optional.of(AccountType.DEMAND_DEPOSIT));
        when(transferLimitPort.findOneTimeLimit(1L)).thenReturn(1_000_000L);
        when(scheduledTransferPersistencePort.existsActiveDuplicate(eq(1L), eq(2L), eq("110987654321"), eq(10_000L), any()))
                .thenReturn(true);

        assertThatThrownBy(() -> scheduledTransferCommandService.register(validCommandBuilder().build()))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(ScheduledTransferErrorCode.DUPLICATE_REGISTRATION));

        verify(scheduledTransferPersistencePort, never()).save(any());
    }

    @Test
    @DisplayName("잔액·1일한도 조회 포트 자체가 없어 등록 시점에는 검증되지 않는다 (REQ-SCD-006, 실행 시점 검증)")
    void register_doesNotCheckBalanceOrDailyLimitAtRegistration() {
        stubClock();
        when(accountStatusPort.belongsToCustomer(2L, 1L)).thenReturn(true);
        when(accountStatusPort.isActiveAccount(2L)).thenReturn(true);
        when(accountStatusPort.isWithdrawalRegistered(2L)).thenReturn(true);
        when(accountStatusPort.findAccountTypeByNumber("110987654321")).thenReturn(Optional.of(AccountType.DEMAND_DEPOSIT));
        when(transferLimitPort.findOneTimeLimit(1L)).thenReturn(1_000_000L);
        when(scheduledTransferPersistencePort.existsActiveDuplicate(eq(1L), eq(2L), eq("110987654321"), eq(10_000L), any()))
                .thenReturn(false);
        when(scheduledTransferPersistencePort.save(any(ScheduledTransfer.class))).thenAnswer(invocation -> {
            ScheduledTransfer arg = invocation.getArgument(0);
            return ScheduledTransfer.reconstitute(
                    100L, arg.getCustomerId(), arg.getWithdrawalAccountId(), arg.getPayeeBankCode(), arg.getPayeeAccountNumber(),
                    arg.getPayeeName(), arg.getAmount(), arg.getScheduledDate(), arg.getMyPassbookMemo(), arg.getRecipientPassbookMemo(),
                    arg.getStatus(), arg.getTransactionNumber(), arg.getRegisteredAt(), arg.getExecutedAt(), arg.getCanceledAt(),
                    arg.getFailureReason());
        });

        // 1회한도(transferLimitPort.findOneTimeLimit)만 호출되고, 잔액·1일한도를 확인하는 별도 포트는
        // 이 서비스에 아예 주입되어 있지 않다 — 등록 시점에는 물리적으로 검증할 수 없는 구조임을 보증한다.
        scheduledTransferCommandService.register(validCommandBuilder().build());

        verify(transferLimitPort).findOneTimeLimit(1L);
    }

    private ScheduledTransfer existingScheduledTransfer(ScheduledTransferStatus status, LocalDate scheduledDate) {
        return ScheduledTransfer.reconstitute(
                10L, 1L, 2L, "088", "110987654321", "홍길동",
                10_000L, scheduledDate, "내메모", "받는메모", status,
                null, java.time.LocalDateTime.now(), null, null, null);
    }

    private ScheduledTransferCancelCommand.ScheduledTransferCancelCommandBuilder validCancelCommandBuilder() {
        return ScheduledTransferCancelCommand.builder()
                .customerId(1L)
                .accountPasswordAuthToken("valid-token")
                .otpAuthToken("valid-otp-token")
                .requestIp("127.0.0.1");
    }

    @Test
    @DisplayName("WAITING 건을 정상적으로 취소하면 저장하고 감사로그를 남긴다")
    void cancel_success() {
        stubClock();
        ScheduledTransfer existing = existingScheduledTransfer(ScheduledTransferStatus.WAITING, LocalDate.now(clock).plusDays(10));
        when(scheduledTransferPersistencePort.findById(10L)).thenReturn(Optional.of(existing));
        when(scheduledTransferPersistencePort.save(any(ScheduledTransfer.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ScheduledTransfer result = scheduledTransferCommandService.cancel(10L, validCancelCommandBuilder().build());

        assertThat(result.getStatus()).isEqualTo(ScheduledTransferStatus.CANCELED);
        verify(authTokenVerificationPort).verify(eq("valid-token"), eq(2L), anyString());
        verify(authTokenVerificationPort).verify(eq("valid-otp-token"), eq(2L), anyString());
        verify(scheduledTransferPersistencePort).save(any(ScheduledTransfer.class));
        verify(auditLogService).record(eq(1L), isNull(), eq(AuditEventType.SCHEDULED_TRANSFER_INFO_CHANGE),
                eq("127.0.0.1"), eq(true), any());
    }

    @Test
    @DisplayName("대상 예약이체가 없으면 NOT_FOUND를 던진다")
    void cancel_notFound_throws() {
        when(scheduledTransferPersistencePort.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> scheduledTransferCommandService.cancel(999L, validCancelCommandBuilder().build()))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(ScheduledTransferErrorCode.NOT_FOUND));

        verify(authTokenVerificationPort, never()).verify(any(), any(), any());
    }

    @Test
    @DisplayName("소유자가 아니면 존재를 숨기기 위해 NOT_FOUND(404)를 던진다 (api_conventions.md §8-3)")
    void cancel_customerIdMismatch_throwsNotFound() {
        stubClock();
        ScheduledTransfer existing = existingScheduledTransfer(ScheduledTransferStatus.WAITING, LocalDate.now(clock).plusDays(10));
        when(scheduledTransferPersistencePort.findById(10L)).thenReturn(Optional.of(existing));

        ScheduledTransferCancelCommand command = validCancelCommandBuilder().customerId(999L).build();

        assertThatThrownBy(() -> scheduledTransferCommandService.cancel(10L, command))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(ScheduledTransferErrorCode.NOT_FOUND));

        verify(authTokenVerificationPort, never()).verify(any(), any(), any());
        verify(scheduledTransferPersistencePort, never()).save(any());
    }

    @Test
    @DisplayName("이미 CANCELED면 재검증·저장 없이 그대로 멱등 성공 반환한다")
    void cancel_alreadyCanceled_returnsIdempotentSuccess() {
        stubClock();
        ScheduledTransfer existing = existingScheduledTransfer(ScheduledTransferStatus.CANCELED, LocalDate.now(clock).plusDays(10));
        when(scheduledTransferPersistencePort.findById(10L)).thenReturn(Optional.of(existing));

        ScheduledTransfer result = scheduledTransferCommandService.cancel(10L, validCancelCommandBuilder().build());

        assertThat(result.getStatus()).isEqualTo(ScheduledTransferStatus.CANCELED);
        verify(authTokenVerificationPort, never()).verify(any(), any(), any());
        verify(scheduledTransferPersistencePort, never()).save(any());
    }

    @Test
    @DisplayName("PROCESSING/SUCCESS/FAILED 상태면 NOT_IN_WAITING_STATUS(SCD0302)를 던진다")
    void cancel_notWaitingStatus_throwsNotInWaitingStatus() {
        stubClock();
        ScheduledTransfer existing = existingScheduledTransfer(ScheduledTransferStatus.PROCESSING, LocalDate.now(clock).plusDays(10));
        when(scheduledTransferPersistencePort.findById(10L)).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> scheduledTransferCommandService.cancel(10L, validCancelCommandBuilder().build()))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(ScheduledTransferErrorCode.NOT_IN_WAITING_STATUS));

        verify(authTokenVerificationPort, never()).verify(any(), any(), any());
    }

    @Test
    @DisplayName("예정일 당일이면 인증 포트를 호출하지 않고 CANNOT_CANCEL_ON_EXECUTION_DATE(SCD0303)를 던진다")
    void cancel_onScheduledDate_doesNotCallAuthTokenVerification() {
        stubClock();
        ScheduledTransfer existing = existingScheduledTransfer(ScheduledTransferStatus.WAITING, LocalDate.now(clock));
        when(scheduledTransferPersistencePort.findById(10L)).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> scheduledTransferCommandService.cancel(10L, validCancelCommandBuilder().build()))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(ScheduledTransferErrorCode.CANNOT_CANCEL_ON_EXECUTION_DATE));

        verify(authTokenVerificationPort, never()).verify(any(), any(), any());
    }

    @Test
    @DisplayName("인증 토큰이 유효하지 않으면 예외가 전파되고 저장하지 않는다")
    void cancel_invalidAuthToken_propagatesException() {
        stubClock();
        ScheduledTransfer existing = existingScheduledTransfer(ScheduledTransferStatus.WAITING, LocalDate.now(clock).plusDays(10));
        when(scheduledTransferPersistencePort.findById(10L)).thenReturn(Optional.of(existing));
        doThrow(new BusinessException(CommonErrorCode.UNAUTHORIZED))
                .when(authTokenVerificationPort).verify(anyString(), any(), anyString());

        assertThatThrownBy(() -> scheduledTransferCommandService.cancel(10L, validCancelCommandBuilder().build()))
                .isInstanceOf(BusinessException.class);

        verify(scheduledTransferPersistencePort, never()).save(any());
    }
}
