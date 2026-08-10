package com.shinhan.corebank.autotransfer.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.shinhan.corebank.account.domain.AccountType;
import com.shinhan.corebank.autotransfer.application.port.in.AutoTransferCancelCommand;
import com.shinhan.corebank.autotransfer.application.port.in.AutoTransferChangeCommand;
import com.shinhan.corebank.common.audit.AuditEventType;
import com.shinhan.corebank.autotransfer.application.port.in.AutoTransferRegisterCommand;
import com.shinhan.corebank.autotransfer.application.port.out.AccountStatusPort;
import com.shinhan.corebank.autotransfer.application.port.out.AuthTokenVerificationPort;
import com.shinhan.corebank.autotransfer.application.port.out.AutoTransferPersistencePort;
import com.shinhan.corebank.autotransfer.application.port.out.TransferLimitPort;
import com.shinhan.corebank.autotransfer.domain.AutoTransfer;
import com.shinhan.corebank.autotransfer.domain.AutoTransferErrorCode;
import com.shinhan.corebank.autotransfer.domain.AutoTransferStatus;
import com.shinhan.corebank.common.audit.AuditLogService;
import com.shinhan.corebank.common.exception.BusinessException;
import com.shinhan.corebank.common.exception.CommonErrorCode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AutoTransferCommandServiceTest {

    @Mock
    AutoTransferPersistencePort autoTransferPersistencePort;

    @Mock
    AuthTokenVerificationPort authTokenVerificationPort;

    @Mock
    AccountStatusPort accountStatusPort;

    @Mock
    TransferLimitPort transferLimitPort;

    @Mock
    AuditLogService auditLogService;

    @InjectMocks
    AutoTransferCommandService autoTransferCommandService;

    private AutoTransfer existingAutoTransfer() {
        // findById()로 DB에서 막 가져온 상황을 흉내내야 하므로, autoTransferId가 없는 register()가 아니라
        // 이미 ID가 채번된 상태를 표현하는 reconstitute()를 사용한다.
        return AutoTransfer.reconstitute(
                10L, 1L, 2L, "110987654321", "홍길동",
                10_000L, 1, 15,
                LocalDate.now().plusDays(10), LocalDate.now().plusMonths(12), LocalDate.now().plusDays(10).plusDays(4),
                "내메모", "받는메모", AutoTransferStatus.NORMAL,
                LocalDateTime.now(), null, LocalDateTime.now());
    }

    private AutoTransferChangeCommand.AutoTransferChangeCommandBuilder validChangeCommandBuilder() {
        return AutoTransferChangeCommand.builder()
                .customerId(1L)
                .amount(20_000L)
                .cycleMonths(3)
                .endDate(LocalDate.now().plusYears(2))
                .myPassbookMemo("새메모")
                .recipientPassbookMemo("새받는메모")
                .authToken("valid-token")
                .requestIp("127.0.0.1");
    }

    private AutoTransferCancelCommand.AutoTransferCancelCommandBuilder validCancelCommandBuilder() {
        return AutoTransferCancelCommand.builder()
                .customerId(1L)
                .authToken("valid-token")
                .requestIp("127.0.0.1");
    }

    private AutoTransferRegisterCommand.AutoTransferRegisterCommandBuilder validCommandBuilder() {
        return AutoTransferRegisterCommand.builder()
                .customerId(1L)
                .withdrawalAccountId(2L)
                .depositAccountNumber("110987654321")
                .payeeName("홍길동")
                .amount(10_000L)
                .cycleMonths(1)
                .transferDay(15)
                .startDate(LocalDate.now().plusDays(10))
                .endDate(LocalDate.now().plusMonths(12))
                .myPassbookMemo("내메모")
                .recipientPassbookMemo("받는메모")
                .authToken("valid-token");
    }

    @Test
    @DisplayName("모든 검증을 통과하면 등록하고 저장된 결과를 반환한다")
    void register_success() {
        when(accountStatusPort.isActiveAccount(2L)).thenReturn(true);
        when(accountStatusPort.findAccountTypeByNumber("110987654321")).thenReturn(Optional.of(AccountType.DEMAND_DEPOSIT));
        when(transferLimitPort.findOneTimeLimit(1L)).thenReturn(1_000_000L);
        when(autoTransferPersistencePort.existsActiveDuplicate(2L, "110987654321", 15)).thenReturn(false);
        when(autoTransferPersistencePort.save(any(AutoTransfer.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AutoTransfer result = autoTransferCommandService.register(validCommandBuilder().build());

        assertThat(result.getWithdrawalAccountId()).isEqualTo(2L);
        assertThat(result.getAmount()).isEqualTo(10_000L);
        verify(autoTransferPersistencePort).save(any(AutoTransfer.class));
    }

    @Test
    @DisplayName("인증 토큰이 유효하지 않으면 그 자리에서 예외가 전파되고 이후 검증은 실행되지 않는다")
    void register_invalidAuthToken_propagatesException() {
        doThrow(new BusinessException(CommonErrorCode.UNAUTHORIZED))
                .when(authTokenVerificationPort).verify(anyString(), any(), anyString());

        assertThatThrownBy(() -> autoTransferCommandService.register(validCommandBuilder().build()))
                .isInstanceOf(BusinessException.class);

        verify(accountStatusPort, never()).isActiveAccount(any());
    }

    @Test
    @DisplayName("출금계좌가 비활성 상태면 ACCOUNT_NOT_ACCESSIBLE을 던진다")
    void register_inactiveWithdrawalAccount_throwsAccountNotAccessible() {
        when(accountStatusPort.isActiveAccount(2L)).thenReturn(false);

        assertThatThrownBy(() -> autoTransferCommandService.register(validCommandBuilder().build()))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(AutoTransferErrorCode.ACCOUNT_NOT_ACCESSIBLE));

        verify(autoTransferPersistencePort, never()).existsActiveDuplicate(any(), any(), anyInt());
    }

    @Test
    @DisplayName("입금계좌가 존재하지 않으면 ACCOUNT_NOT_ACCESSIBLE을 던진다")
    void register_depositAccountNotFound_throwsAccountNotAccessible() {
        when(accountStatusPort.isActiveAccount(2L)).thenReturn(true);
        when(accountStatusPort.findAccountTypeByNumber("110987654321")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> autoTransferCommandService.register(validCommandBuilder().build()))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(AutoTransferErrorCode.ACCOUNT_NOT_ACCESSIBLE));
    }

    @Test
    @DisplayName("입금계좌 유형이 입출금계좌가 아니면 UNSUPPORTED_DEPOSIT_ACCOUNT_TYPE을 던진다")
    void register_unsupportedDepositAccountType_throws() {
        when(accountStatusPort.isActiveAccount(2L)).thenReturn(true);
        when(accountStatusPort.findAccountTypeByNumber("110987654321")).thenReturn(Optional.of(AccountType.TIME_DEPOSIT));

        assertThatThrownBy(() -> autoTransferCommandService.register(validCommandBuilder().build()))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(AutoTransferErrorCode.UNSUPPORTED_DEPOSIT_ACCOUNT_TYPE));
    }

    @Test
    @DisplayName("1회 이체한도를 초과하면 ONE_TIME_LIMIT_EXCEEDED를 던진다")
    void register_exceedsOneTimeLimit_throws() {
        when(accountStatusPort.isActiveAccount(2L)).thenReturn(true);
        when(accountStatusPort.findAccountTypeByNumber("110987654321")).thenReturn(Optional.of(AccountType.DEMAND_DEPOSIT));
        when(transferLimitPort.findOneTimeLimit(1L)).thenReturn(5_000L);

        assertThatThrownBy(() -> autoTransferCommandService.register(validCommandBuilder().build()))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(AutoTransferErrorCode.ONE_TIME_LIMIT_EXCEEDED));

        verify(autoTransferPersistencePort, never()).existsActiveDuplicate(any(), any(), anyInt());
    }

    @Test
    @DisplayName("동일 조건의 정상 자동이체가 이미 있으면 DUPLICATE_REGISTRATION을 던진다")
    void register_duplicateRegistration_throws() {
        when(accountStatusPort.isActiveAccount(2L)).thenReturn(true);
        when(accountStatusPort.findAccountTypeByNumber("110987654321")).thenReturn(Optional.of(AccountType.DEMAND_DEPOSIT));
        when(transferLimitPort.findOneTimeLimit(1L)).thenReturn(1_000_000L);
        when(autoTransferPersistencePort.existsActiveDuplicate(2L, "110987654321", 15)).thenReturn(true);

        assertThatThrownBy(() -> autoTransferCommandService.register(validCommandBuilder().build()))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(AutoTransferErrorCode.DUPLICATE_REGISTRATION));

        verify(autoTransferPersistencePort, never()).save(any());
    }

    @Test
    @DisplayName("정상적으로 변경하면 저장하고 감사로그를 남긴다")
    void change_success() {
        AutoTransfer existing = existingAutoTransfer();
        when(autoTransferPersistencePort.findById(10L)).thenReturn(Optional.of(existing));
        when(autoTransferPersistencePort.save(any(AutoTransfer.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AutoTransfer result = autoTransferCommandService.change(10L, validChangeCommandBuilder().build());

        assertThat(result.getAmount()).isEqualTo(20_000L);
        assertThat(result.getCycleMonths()).isEqualTo(3);
        verify(autoTransferPersistencePort).save(any(AutoTransfer.class));
        verify(auditLogService).record(eq(1L), isNull(), eq(AuditEventType.AUTO_TRANSFER_INFO_CHANGE),
                eq("127.0.0.1"), eq(true), any());
    }

    @Test
    @DisplayName("일부 필드만 보내면 나머지 필드는 기존 값을 유지한 채 저장된다")
    void change_partialFields_keepsUnspecifiedFieldsUnchanged() {
        AutoTransfer existing = existingAutoTransfer();
        when(autoTransferPersistencePort.findById(10L)).thenReturn(Optional.of(existing));
        when(autoTransferPersistencePort.save(any(AutoTransfer.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AutoTransferChangeCommand command = AutoTransferChangeCommand.builder()
                .customerId(1L)
                .amount(30_000L)
                .authToken("valid-token")
                .requestIp("127.0.0.1")
                .build();

        AutoTransfer result = autoTransferCommandService.change(10L, command);

        assertThat(result.getAmount()).isEqualTo(30_000L);
        assertThat(result.getCycleMonths()).isEqualTo(1);
        assertThat(result.getMyPassbookMemo()).isEqualTo("내메모");
        assertThat(result.getRecipientPassbookMemo()).isEqualTo("받는메모");
    }

    @Test
    @DisplayName("대상 자동이체가 없으면 NOT_FOUND를 던진다")
    void change_notFound_throws() {
        when(autoTransferPersistencePort.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> autoTransferCommandService.change(999L, validChangeCommandBuilder().build()))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(AutoTransferErrorCode.NOT_FOUND));

        verify(authTokenVerificationPort, never()).verify(any(), any(), any());
    }

    @Test
    @DisplayName("인증 토큰이 유효하지 않으면 예외가 전파되고 저장하지 않는다")
    void change_invalidAuthToken_propagatesException() {
        AutoTransfer existing = existingAutoTransfer();
        when(autoTransferPersistencePort.findById(10L)).thenReturn(Optional.of(existing));
        doThrow(new BusinessException(CommonErrorCode.UNAUTHORIZED))
                .when(authTokenVerificationPort).verify(anyString(), any(), anyString());

        assertThatThrownBy(() -> autoTransferCommandService.change(10L, validChangeCommandBuilder().build()))
                .isInstanceOf(BusinessException.class);

        verify(autoTransferPersistencePort, never()).save(any());
        verify(auditLogService, never()).record(any(), any(), any(), any(), anyBoolean(), any());
    }

    @Test
    @DisplayName("정상적으로 해지하면 저장하고 감사로그를 남긴다")
    void cancel_success() {
        AutoTransfer existing = existingAutoTransfer();
        when(autoTransferPersistencePort.findById(10L)).thenReturn(Optional.of(existing));
        when(autoTransferPersistencePort.save(any(AutoTransfer.class))).thenAnswer(invocation -> invocation.getArgument(0));

        autoTransferCommandService.cancel(10L, validCancelCommandBuilder().build());

        assertThat(existing.getStatus()).isEqualTo(AutoTransferStatus.TERMINATED);
        verify(autoTransferPersistencePort).save(any(AutoTransfer.class));
        verify(auditLogService).record(eq(1L), isNull(), eq(AuditEventType.AUTO_TRANSFER_INFO_CHANGE),
                eq("127.0.0.1"), eq(true), any());
    }

    @Test
    @DisplayName("대상 자동이체가 없으면 NOT_FOUND를 던진다")
    void cancel_notFound_throws() {
        when(autoTransferPersistencePort.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> autoTransferCommandService.cancel(999L, validCancelCommandBuilder().build()))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(AutoTransferErrorCode.NOT_FOUND));

        verify(authTokenVerificationPort, never()).verify(any(), any(), any());
    }

    @Test
    @DisplayName("인증 토큰이 유효하지 않으면 예외가 전파되고 저장하지 않는다")
    void cancel_invalidAuthToken_propagatesException() {
        AutoTransfer existing = existingAutoTransfer();
        when(autoTransferPersistencePort.findById(10L)).thenReturn(Optional.of(existing));
        doThrow(new BusinessException(CommonErrorCode.UNAUTHORIZED))
                .when(authTokenVerificationPort).verify(anyString(), any(), anyString());

        assertThatThrownBy(() -> autoTransferCommandService.cancel(10L, validCancelCommandBuilder().build()))
                .isInstanceOf(BusinessException.class);

        verify(autoTransferPersistencePort, never()).save(any());
        verify(auditLogService, never()).record(any(), any(), any(), any(), anyBoolean(), any());
    }
}