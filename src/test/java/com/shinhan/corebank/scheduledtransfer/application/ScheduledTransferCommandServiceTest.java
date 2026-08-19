package com.shinhan.corebank.scheduledtransfer.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.shinhan.corebank.account.domain.AccountType;
import com.shinhan.corebank.common.audit.AuditEventType;
import com.shinhan.corebank.common.audit.AuditLogService;
import com.shinhan.corebank.common.exception.BusinessException;
import com.shinhan.corebank.common.exception.CommonErrorCode;
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
import java.time.ZoneOffset;
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

    private ScheduledTransferRegisterCommand.ScheduledTransferRegisterCommandBuilder validCommandBuilder() {
        return ScheduledTransferRegisterCommand.builder()
                .customerId(1L)
                .withdrawalAccountId(2L)
                .payeeAccountNumber("110987654321")
                .payeeName("홍길동")
                .amount(10_000L)
                .scheduledDate(LocalDate.now().plusDays(10))
                .myPassbookMemo("내메모")
                .recipientPassbookMemo("받는메모")
                .accountPasswordAuthToken("valid-token")
                .requestIp("127.0.0.1");
    }

    // LocalDateTime.now(clock)이 내부적으로 instant()/getZone()을 호출하므로 이 둘을 스텁한다
    private void stubClock() {
        when(clock.instant()).thenReturn(Instant.now());
        when(clock.getZone()).thenReturn(ZoneOffset.UTC);
    }

    @Test
    @DisplayName("모든 검증을 통과하면 등록하고 저장된 결과를 반환한다")
    void register_success() {
        when(accountStatusPort.belongsToCustomer(2L, 1L)).thenReturn(true);
        when(accountStatusPort.isActiveAccount(2L)).thenReturn(true);
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
        verify(auditLogService).record(eq(1L), isNull(), eq(AuditEventType.SCHEDULED_TRANSFER_INFO_CHANGE),
                eq("127.0.0.1"), eq(true), any());
    }

    @Test
    @DisplayName("인증 토큰이 유효하지 않으면 그 자리에서 예외가 전파되고 이후 검증은 실행되지 않는다")
    void register_invalidAuthToken_propagatesException() {
        doThrow(new BusinessException(CommonErrorCode.UNAUTHORIZED))
                .when(authTokenVerificationPort).verify(anyString(), any(), anyString());

        assertThatThrownBy(() -> scheduledTransferCommandService.register(validCommandBuilder().build()))
                .isInstanceOf(BusinessException.class);

        verify(accountStatusPort, never()).belongsToCustomer(any(), any());
    }

    @Test
    @DisplayName("출금계좌가 본인 소유가 아니면 ACCOUNT_NOT_ACCESSIBLE을 던진다")
    void register_accountNotOwnedByCustomer_throwsAccountNotAccessible() {
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
        when(accountStatusPort.belongsToCustomer(2L, 1L)).thenReturn(true);
        when(accountStatusPort.isActiveAccount(2L)).thenReturn(false);

        assertThatThrownBy(() -> scheduledTransferCommandService.register(validCommandBuilder().build()))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(ScheduledTransferErrorCode.ACCOUNT_NOT_ACCESSIBLE));

        verify(scheduledTransferPersistencePort, never()).existsActiveDuplicate(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("입금계좌가 존재하지 않으면 ACCOUNT_NOT_ACCESSIBLE을 던진다")
    void register_payeeAccountNotFound_throwsAccountNotAccessible() {
        when(accountStatusPort.belongsToCustomer(2L, 1L)).thenReturn(true);
        when(accountStatusPort.isActiveAccount(2L)).thenReturn(true);
        when(accountStatusPort.findAccountTypeByNumber("110987654321")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> scheduledTransferCommandService.register(validCommandBuilder().build()))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(ScheduledTransferErrorCode.ACCOUNT_NOT_ACCESSIBLE));
    }

    @Test
    @DisplayName("입금계좌 유형이 입출금계좌가 아니면 UNSUPPORTED_DEPOSIT_ACCOUNT_TYPE을 던진다")
    void register_unsupportedDepositAccountType_throws() {
        when(accountStatusPort.belongsToCustomer(2L, 1L)).thenReturn(true);
        when(accountStatusPort.isActiveAccount(2L)).thenReturn(true);
        when(accountStatusPort.findAccountTypeByNumber("110987654321")).thenReturn(Optional.of(AccountType.TIME_DEPOSIT));

        assertThatThrownBy(() -> scheduledTransferCommandService.register(validCommandBuilder().build()))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(ScheduledTransferErrorCode.UNSUPPORTED_DEPOSIT_ACCOUNT_TYPE));
    }

    @Test
    @DisplayName("1회 이체한도를 초과하면 ONE_TIME_LIMIT_EXCEEDED를 던진다")
    void register_exceedsOneTimeLimit_throws() {
        when(accountStatusPort.belongsToCustomer(2L, 1L)).thenReturn(true);
        when(accountStatusPort.isActiveAccount(2L)).thenReturn(true);
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
        when(accountStatusPort.belongsToCustomer(2L, 1L)).thenReturn(true);
        when(accountStatusPort.isActiveAccount(2L)).thenReturn(true);
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
        when(accountStatusPort.belongsToCustomer(2L, 1L)).thenReturn(true);
        when(accountStatusPort.isActiveAccount(2L)).thenReturn(true);
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

        // 1회한도(transferLimitPort.findOneTimeLimit)만 호출되고, 잔액·1일한도를 확인하는 별도 포트는
        // 이 서비스에 아예 주입되어 있지 않다 — 등록 시점에는 물리적으로 검증할 수 없는 구조임을 보증한다.
        scheduledTransferCommandService.register(validCommandBuilder().build());

        verify(transferLimitPort).findOneTimeLimit(1L);
    }
}
