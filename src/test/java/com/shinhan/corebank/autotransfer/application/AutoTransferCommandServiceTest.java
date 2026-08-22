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
import com.shinhan.corebank.autotransfer.application.port.out.AutoTransferOtpVerificationPort;
import com.shinhan.corebank.autotransfer.application.port.out.AutoTransferPersistencePort;
import com.shinhan.corebank.autotransfer.application.port.out.TransferLimitPort;
import com.shinhan.corebank.autotransfer.domain.AutoTransfer;
import com.shinhan.corebank.autotransfer.domain.AutoTransferErrorCode;
import com.shinhan.corebank.autotransfer.domain.AutoTransferStatus;
import com.shinhan.corebank.common.audit.AuditLogService;
import com.shinhan.corebank.common.exception.BusinessException;
import com.shinhan.corebank.common.exception.CommonErrorCode;
import org.springframework.dao.OptimisticLockingFailureException;
import java.time.Clock;
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
    AutoTransferOtpVerificationPort autoTransferOtpVerificationPort;

    @Mock
    AccountStatusPort accountStatusPort;

    @Mock
    TransferLimitPort transferLimitPort;

    @Mock
    AuditLogService auditLogService;

    @Mock
    Clock clock;

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
                LocalDateTime.now(), null, LocalDateTime.now(), 0L);
    }

    private AutoTransferChangeCommand.AutoTransferChangeCommandBuilder validChangeCommandBuilder() {
        return AutoTransferChangeCommand.builder()
                .customerId(1L)
                .amount(20_000L)
                .cycleMonths(3)
                .endDate(LocalDate.now().plusYears(2))
                .myPassbookMemo("새메모")
                .recipientPassbookMemo("새받는메모")
                .accountPasswordAuthToken("valid-token")
                .otpAuthToken("valid-otp-token")
                .requestIp("127.0.0.1");
    }

    private AutoTransferCancelCommand.AutoTransferCancelCommandBuilder validCancelCommandBuilder() {
        return AutoTransferCancelCommand.builder()
                .customerId(1L)
                .accountPasswordAuthToken("valid-token")
                .otpAuthToken("valid-otp-token")
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
                .accountPasswordAuthToken("valid-token")
                .otpAuthToken("valid-otp-token")
                .requestIp("127.0.0.1");
    }

    @Test
    @DisplayName("모든 검증을 통과하면 등록하고 저장된 결과를 반환한다")
    void register_success() {
        when(accountStatusPort.belongsToCustomer(2L, 1L)).thenReturn(true);
        when(accountStatusPort.isActiveAccount(2L)).thenReturn(true);
        when(accountStatusPort.isWithdrawalRegistered(2L)).thenReturn(true);
        when(accountStatusPort.findAccountTypeByNumber("110987654321")).thenReturn(Optional.of(AccountType.DEMAND_DEPOSIT));
        when(transferLimitPort.findOneTimeLimit(1L)).thenReturn(1_000_000L);
        when(autoTransferPersistencePort.existsActiveDuplicate(2L, "110987654321", 15)).thenReturn(false);
        when(clock.withZone(any())).thenReturn(Clock.systemUTC());
        // 실제 어댑터는 INSERT 후 채번된 ID로 다시 조립해서 돌려준다 — 감사로그가 autoTransferId를 필요로 하므로 그 동작을 흉내낸다
        when(autoTransferPersistencePort.save(any(AutoTransfer.class))).thenAnswer(invocation -> {
            AutoTransfer arg = invocation.getArgument(0);
            return AutoTransfer.reconstitute(
                    100L, arg.getCustomerId(), arg.getWithdrawalAccountId(), arg.getDepositAccountNumber(), arg.getPayeeName(),
                    arg.getAmount(), arg.getCycleMonths(), arg.getTransferDay(), arg.getStartDate(), arg.getEndDate(), arg.getNextExecutionDate(),
                    arg.getMyPassbookMemo(), arg.getRecipientPassbookMemo(), arg.getStatus(), arg.getRegisteredAt(), arg.getTerminatedAt(), arg.getUpdatedAt(), arg.getVersion());
        });

        AutoTransfer result = autoTransferCommandService.register(validCommandBuilder().build());

        assertThat(result.getWithdrawalAccountId()).isEqualTo(2L);
        assertThat(result.getAmount()).isEqualTo(10_000L);
        verify(autoTransferPersistencePort).save(any(AutoTransfer.class));
        verify(autoTransferOtpVerificationPort).verifyRegisterAndConsume(
                eq("valid-otp-token"), eq(1L), eq(2L), eq("110987654321"), eq(10_000L), eq(1), eq(15), any(), any());
        verify(auditLogService).record(eq(1L), isNull(), eq(AuditEventType.AUTO_TRANSFER_INFO_CHANGE),
                eq("127.0.0.1"), eq(true), any());
    }

    // 인증 토큰은 소유권·업무규칙 검증을 모두 통과한 뒤 상태 변경 직전에 검증하므로
    // (otp_integration_guide.md §9), 실패를 재현하려면 그 앞의 모든 검증을 통과시켜야 한다.
    @Test
    @DisplayName("계좌비밀번호 인증 토큰이 유효하지 않으면 예외가 전파되고 저장하지 않는다")
    void register_invalidAccountPasswordAuthToken_propagatesException() {
        when(accountStatusPort.belongsToCustomer(2L, 1L)).thenReturn(true);
        when(accountStatusPort.isActiveAccount(2L)).thenReturn(true);
        when(accountStatusPort.isWithdrawalRegistered(2L)).thenReturn(true);
        when(accountStatusPort.findAccountTypeByNumber("110987654321")).thenReturn(Optional.of(AccountType.DEMAND_DEPOSIT));
        when(transferLimitPort.findOneTimeLimit(1L)).thenReturn(1_000_000L);
        when(autoTransferPersistencePort.existsActiveDuplicate(2L, "110987654321", 15)).thenReturn(false);
        doThrow(new BusinessException(CommonErrorCode.UNAUTHORIZED))
                .when(authTokenVerificationPort).verify(anyString(), any(), anyString());

        assertThatThrownBy(() -> autoTransferCommandService.register(validCommandBuilder().build()))
                .isInstanceOf(BusinessException.class);

        verify(autoTransferOtpVerificationPort, never())
                .verifyRegisterAndConsume(any(), any(), any(), any(), any(), any(), any(), any(), any());
        verify(autoTransferPersistencePort, never()).save(any());
    }

    @Test
    @DisplayName("OTP 인증 토큰이 유효하지 않으면 예외가 전파되고 저장하지 않는다")
    void register_invalidOtpAuthToken_propagatesException() {
        when(accountStatusPort.belongsToCustomer(2L, 1L)).thenReturn(true);
        when(accountStatusPort.isActiveAccount(2L)).thenReturn(true);
        when(accountStatusPort.isWithdrawalRegistered(2L)).thenReturn(true);
        when(accountStatusPort.findAccountTypeByNumber("110987654321")).thenReturn(Optional.of(AccountType.DEMAND_DEPOSIT));
        when(transferLimitPort.findOneTimeLimit(1L)).thenReturn(1_000_000L);
        when(autoTransferPersistencePort.existsActiveDuplicate(2L, "110987654321", 15)).thenReturn(false);
        doThrow(new BusinessException(CommonErrorCode.UNAUTHORIZED))
                .when(autoTransferOtpVerificationPort)
                .verifyRegisterAndConsume(any(), any(), any(), any(), any(), any(), any(), any(), any());

        assertThatThrownBy(() -> autoTransferCommandService.register(validCommandBuilder().build()))
                .isInstanceOf(BusinessException.class);

        verify(autoTransferPersistencePort, never()).save(any());
    }

    @Test
    @DisplayName("출금계좌가 본인 소유가 아니면 ACCOUNT_NOT_ACCESSIBLE을 던진다")
    void register_accountNotOwnedByCustomer_throwsAccountNotAccessible() {
        when(accountStatusPort.belongsToCustomer(2L, 1L)).thenReturn(false);

        assertThatThrownBy(() -> autoTransferCommandService.register(validCommandBuilder().build()))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(AutoTransferErrorCode.ACCOUNT_NOT_ACCESSIBLE));

        verify(accountStatusPort, never()).isActiveAccount(any());
    }

    @Test
    @DisplayName("출금계좌가 비활성 상태면 ACCOUNT_NOT_ACCESSIBLE을 던진다")
    void register_inactiveWithdrawalAccount_throwsAccountNotAccessible() {
        when(accountStatusPort.belongsToCustomer(2L, 1L)).thenReturn(true);
        when(accountStatusPort.isActiveAccount(2L)).thenReturn(false);

        assertThatThrownBy(() -> autoTransferCommandService.register(validCommandBuilder().build()))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(AutoTransferErrorCode.ACCOUNT_NOT_ACCESSIBLE));

        verify(autoTransferPersistencePort, never()).existsActiveDuplicate(any(), any(), anyInt());
    }

    @Test
    @DisplayName("출금계좌로 등록되지 않은 계좌면 ACCOUNT_NOT_ACCESSIBLE을 던진다")
    void register_withdrawalAccountNotRegistered_throwsAccountNotAccessible() {
        when(accountStatusPort.belongsToCustomer(2L, 1L)).thenReturn(true);
        when(accountStatusPort.isActiveAccount(2L)).thenReturn(true);
        when(accountStatusPort.isWithdrawalRegistered(2L)).thenReturn(false);

        assertThatThrownBy(() -> autoTransferCommandService.register(validCommandBuilder().build()))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(AutoTransferErrorCode.ACCOUNT_NOT_ACCESSIBLE));

        verify(accountStatusPort, never()).findAccountTypeByNumber(any());
        verify(autoTransferPersistencePort, never()).existsActiveDuplicate(any(), any(), anyInt());
    }

    @Test
    @DisplayName("입금계좌가 존재하지 않으면 ACCOUNT_NOT_ACCESSIBLE을 던진다")
    void register_depositAccountNotFound_throwsAccountNotAccessible() {
        when(accountStatusPort.belongsToCustomer(2L, 1L)).thenReturn(true);
        when(accountStatusPort.isActiveAccount(2L)).thenReturn(true);
        when(accountStatusPort.isWithdrawalRegistered(2L)).thenReturn(true);
        when(accountStatusPort.findAccountTypeByNumber("110987654321")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> autoTransferCommandService.register(validCommandBuilder().build()))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(AutoTransferErrorCode.ACCOUNT_NOT_ACCESSIBLE));
    }

    @Test
    @DisplayName("입금계좌 유형이 입출금계좌가 아니면 UNSUPPORTED_DEPOSIT_ACCOUNT_TYPE을 던진다")
    void register_unsupportedDepositAccountType_throws() {
        when(accountStatusPort.belongsToCustomer(2L, 1L)).thenReturn(true);
        when(accountStatusPort.isActiveAccount(2L)).thenReturn(true);
        when(accountStatusPort.isWithdrawalRegistered(2L)).thenReturn(true);
        when(accountStatusPort.findAccountTypeByNumber("110987654321")).thenReturn(Optional.of(AccountType.TIME_DEPOSIT));

        assertThatThrownBy(() -> autoTransferCommandService.register(validCommandBuilder().build()))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(AutoTransferErrorCode.UNSUPPORTED_DEPOSIT_ACCOUNT_TYPE));
    }

    @Test
    @DisplayName("1회 이체한도를 초과하면 ONE_TIME_LIMIT_EXCEEDED를 던진다")
    void register_exceedsOneTimeLimit_throws() {
        when(accountStatusPort.belongsToCustomer(2L, 1L)).thenReturn(true);
        when(accountStatusPort.isActiveAccount(2L)).thenReturn(true);
        when(accountStatusPort.isWithdrawalRegistered(2L)).thenReturn(true);
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
        when(accountStatusPort.belongsToCustomer(2L, 1L)).thenReturn(true);
        when(accountStatusPort.isActiveAccount(2L)).thenReturn(true);
        when(accountStatusPort.isWithdrawalRegistered(2L)).thenReturn(true);
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
        when(transferLimitPort.findOneTimeLimit(1L)).thenReturn(1_000_000L);
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
        when(transferLimitPort.findOneTimeLimit(1L)).thenReturn(1_000_000L);
        when(autoTransferPersistencePort.save(any(AutoTransfer.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AutoTransferChangeCommand command = AutoTransferChangeCommand.builder()
                .customerId(1L)
                .amount(30_000L)
                .accountPasswordAuthToken("valid-token")
                .otpAuthToken("valid-otp-token")
                .requestIp("127.0.0.1")
                .build();

        AutoTransfer result = autoTransferCommandService.change(10L, command);

        assertThat(result.getAmount()).isEqualTo(30_000L);
        assertThat(result.getCycleMonths()).isEqualTo(1);
        assertThat(result.getMyPassbookMemo()).isEqualTo("내메모");
        assertThat(result.getRecipientPassbookMemo()).isEqualTo("받는메모");
    }

    @Test
    @DisplayName("변경 금액이 1회 이체한도를 초과하면 ONE_TIME_LIMIT_EXCEEDED를 던지고 저장하지 않는다")
    void change_amountExceedsOneTimeLimit_throws() {
        AutoTransfer existing = existingAutoTransfer();
        when(autoTransferPersistencePort.findById(10L)).thenReturn(Optional.of(existing));
        when(transferLimitPort.findOneTimeLimit(1L)).thenReturn(5_000L);

        AutoTransferChangeCommand command = validChangeCommandBuilder().amount(20_000L).build();

        assertThatThrownBy(() -> autoTransferCommandService.change(10L, command))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(AutoTransferErrorCode.ONE_TIME_LIMIT_EXCEEDED));

        verify(autoTransferPersistencePort, never()).save(any());
    }

    @Test
    @DisplayName("정상 상태가 아닌 건은 한도 초과 금액을 보내도 AUT0006이 아니라 AUT0302를 던진다")
    void change_notModifiableStatus_throwsNotInNormalStatus_evenWithOverLimitAmount() {
        AutoTransfer terminated = AutoTransfer.reconstitute(
                10L, 1L, 2L, "110987654321", "홍길동",
                10_000L, 1, 15,
                LocalDate.now().plusDays(10), LocalDate.now().plusMonths(12), null,
                "내메모", "받는메모", AutoTransferStatus.TERMINATED,
                LocalDateTime.now(), LocalDateTime.now(), LocalDateTime.now(), 0L);
        when(autoTransferPersistencePort.findById(10L)).thenReturn(Optional.of(terminated));

        AutoTransferChangeCommand command = validChangeCommandBuilder().amount(20_000L).build();

        assertThatThrownBy(() -> autoTransferCommandService.change(10L, command))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(AutoTransferErrorCode.NOT_IN_NORMAL_STATUS));

        // 상태 검증이 한도 검증보다 먼저 실행돼야 하므로, 한도 조회 자체가 일어나면 안 된다
        verify(transferLimitPort, never()).findOneTimeLimit(any());
        verify(autoTransferPersistencePort, never()).save(any());
    }

    @Test
    @DisplayName("금액을 안 바꾸면 이체한도를 재검증하지 않는다")
    void change_amountNotProvided_skipsLimitCheck() {
        AutoTransfer existing = existingAutoTransfer();
        when(autoTransferPersistencePort.findById(10L)).thenReturn(Optional.of(existing));
        when(autoTransferPersistencePort.save(any(AutoTransfer.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AutoTransferChangeCommand command = AutoTransferChangeCommand.builder()
                .customerId(1L)
                .cycleMonths(3)
                .accountPasswordAuthToken("valid-token")
                .otpAuthToken("valid-otp-token")
                .requestIp("127.0.0.1")
                .build();

        autoTransferCommandService.change(10L, command);

        verify(transferLimitPort, never()).findOneTimeLimit(any());
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
    @DisplayName("소유자가 아니면(customerId 불일치) 존재를 숨기고 NOT_FOUND를 던진다")
    void change_customerIdMismatch_throwsNotFound() {
        AutoTransfer existing = existingAutoTransfer();
        when(autoTransferPersistencePort.findById(10L)).thenReturn(Optional.of(existing));

        AutoTransferChangeCommand command = validChangeCommandBuilder().customerId(999L).build();

        assertThatThrownBy(() -> autoTransferCommandService.change(10L, command))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(AutoTransferErrorCode.NOT_FOUND));

        verify(authTokenVerificationPort, never()).verify(any(), any(), any());
        verify(autoTransferPersistencePort, never()).save(any());
    }

    @Test
    @DisplayName("계좌비밀번호 인증 토큰이 유효하지 않으면 예외가 전파되고 저장하지 않는다")
    void change_invalidAuthToken_propagatesException() {
        AutoTransfer existing = existingAutoTransfer();
        when(autoTransferPersistencePort.findById(10L)).thenReturn(Optional.of(existing));
        when(transferLimitPort.findOneTimeLimit(1L)).thenReturn(1_000_000L);
        doThrow(new BusinessException(CommonErrorCode.UNAUTHORIZED))
                .when(authTokenVerificationPort).verify(anyString(), any(), anyString());

        assertThatThrownBy(() -> autoTransferCommandService.change(10L, validChangeCommandBuilder().build()))
                .isInstanceOf(BusinessException.class);

        verify(autoTransferOtpVerificationPort, never()).verifyChangeAndConsume(any(), any(), any(), any(), any(), any());
        verify(autoTransferPersistencePort, never()).save(any());
        verify(auditLogService, never()).record(any(), any(), any(), any(), anyBoolean(), any());
    }

    @Test
    @DisplayName("OTP 인증 토큰이 유효하지 않으면 예외가 전파되고 저장하지 않는다")
    void change_invalidOtpAuthToken_propagatesException() {
        AutoTransfer existing = existingAutoTransfer();
        when(autoTransferPersistencePort.findById(10L)).thenReturn(Optional.of(existing));
        when(transferLimitPort.findOneTimeLimit(1L)).thenReturn(1_000_000L);
        doThrow(new BusinessException(CommonErrorCode.UNAUTHORIZED))
                .when(autoTransferOtpVerificationPort).verifyChangeAndConsume(any(), any(), any(), any(), any(), any());

        assertThatThrownBy(() -> autoTransferCommandService.change(10L, validChangeCommandBuilder().build()))
                .isInstanceOf(BusinessException.class);

        verify(autoTransferPersistencePort, never()).save(any());
        verify(auditLogService, never()).record(any(), any(), any(), any(), anyBoolean(), any());
    }

    @Test
    @DisplayName("저장 시점에 낙관적 락 충돌(OptimisticLockingFailureException)이 나면 삼키지 않고 그대로 전파한다")
    void change_optimisticLockConflict_propagatesException() {
        // change()는 findById()로 매번 최신 상태를 읽어와 그 자리에서 바로 저장하므로, 순차 테스트로는
        // 진짜 버전 충돌을 재현할 수 없다(재현하려면 진짜 동시성이 필요) - 대신 영속성 포트가 실제로
        // 충돌을 던졌을 때 서비스가 그걸 삼키지 않고 그대로 전파하는지만 검증한다. HTTP 레벨 매핑
        // (CONCURRENT_MODIFICATION 응답)은 ApiExceptionHandler의 공용 핸들러가 이미 담당한다.
        AutoTransfer existing = existingAutoTransfer();
        when(autoTransferPersistencePort.findById(10L)).thenReturn(Optional.of(existing));
        when(transferLimitPort.findOneTimeLimit(1L)).thenReturn(1_000_000L);
        when(autoTransferPersistencePort.save(any(AutoTransfer.class)))
                .thenThrow(new OptimisticLockingFailureException("다른 요청이 먼저 이 자동이체를 변경했습니다"));

        assertThatThrownBy(() -> autoTransferCommandService.change(10L, validChangeCommandBuilder().build()))
                .isInstanceOf(OptimisticLockingFailureException.class);
    }

    @Test
    @DisplayName("정상적으로 해지하면 저장하고 감사로그를 남긴다")
    void cancel_success() {
        AutoTransfer existing = existingAutoTransfer();
        when(autoTransferPersistencePort.findById(10L)).thenReturn(Optional.of(existing));
        when(autoTransferPersistencePort.save(any(AutoTransfer.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(clock.withZone(any())).thenReturn(Clock.systemUTC());

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
    @DisplayName("소유자가 아니면(customerId 불일치) 존재를 숨기고 NOT_FOUND를 던진다")
    void cancel_customerIdMismatch_throwsNotFound() {
        AutoTransfer existing = existingAutoTransfer();
        when(autoTransferPersistencePort.findById(10L)).thenReturn(Optional.of(existing));

        AutoTransferCancelCommand command = validCancelCommandBuilder().customerId(999L).build();

        assertThatThrownBy(() -> autoTransferCommandService.cancel(10L, command))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(AutoTransferErrorCode.NOT_FOUND));

        verify(authTokenVerificationPort, never()).verify(any(), any(), any());
        verify(autoTransferPersistencePort, never()).save(any());
    }

    @Test
    @DisplayName("계좌비밀번호 인증 토큰이 유효하지 않으면 예외가 전파되고 저장하지 않는다")
    void cancel_invalidAuthToken_propagatesException() {
        AutoTransfer existing = existingAutoTransfer();
        when(autoTransferPersistencePort.findById(10L)).thenReturn(Optional.of(existing));
        when(clock.withZone(any())).thenReturn(Clock.systemUTC());
        doThrow(new BusinessException(CommonErrorCode.UNAUTHORIZED))
                .when(authTokenVerificationPort).verify(anyString(), any(), anyString());

        assertThatThrownBy(() -> autoTransferCommandService.cancel(10L, validCancelCommandBuilder().build()))
                .isInstanceOf(BusinessException.class);

        verify(autoTransferOtpVerificationPort, never()).verifyCancelAndConsume(any(), any(), any());
        verify(autoTransferPersistencePort, never()).save(any());
        verify(auditLogService, never()).record(any(), any(), any(), any(), anyBoolean(), any());
    }

    @Test
    @DisplayName("OTP 인증 토큰이 유효하지 않으면 예외가 전파되고 저장하지 않는다")
    void cancel_invalidOtpAuthToken_propagatesException() {
        AutoTransfer existing = existingAutoTransfer();
        when(autoTransferPersistencePort.findById(10L)).thenReturn(Optional.of(existing));
        when(clock.withZone(any())).thenReturn(Clock.systemUTC());
        doThrow(new BusinessException(CommonErrorCode.UNAUTHORIZED))
                .when(autoTransferOtpVerificationPort).verifyCancelAndConsume(any(), any(), any());

        assertThatThrownBy(() -> autoTransferCommandService.cancel(10L, validCancelCommandBuilder().build()))
                .isInstanceOf(BusinessException.class);

        verify(autoTransferPersistencePort, never()).save(any());
        verify(auditLogService, never()).record(any(), any(), any(), any(), anyBoolean(), any());
    }

    @Test
    @DisplayName("정상 상태가 아닌 건은 해지 요청도 OTP를 소비하지 않고 AUT0302를 던진다")
    void cancel_notModifiableStatus_throwsNotInNormalStatus_withoutConsumingOtp() {
        AutoTransfer terminated = AutoTransfer.reconstitute(
                10L, 1L, 2L, "110987654321", "홍길동",
                10_000L, 1, 15,
                LocalDate.now().plusDays(10), LocalDate.now().plusMonths(12), null,
                "내메모", "받는메모", AutoTransferStatus.TERMINATED,
                LocalDateTime.now(), LocalDateTime.now(), LocalDateTime.now(), 0L);
        when(autoTransferPersistencePort.findById(10L)).thenReturn(Optional.of(terminated));
        when(clock.withZone(any())).thenReturn(Clock.systemUTC());

        assertThatThrownBy(() -> autoTransferCommandService.cancel(10L, validCancelCommandBuilder().build()))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(AutoTransferErrorCode.NOT_IN_NORMAL_STATUS));

        verify(authTokenVerificationPort, never()).verify(any(), any(), any());
        verify(autoTransferOtpVerificationPort, never()).verifyCancelAndConsume(any(), any(), any());
        verify(autoTransferPersistencePort, never()).save(any());
    }

    @Test
    @DisplayName("다음 실행 예정일 당일 해지 요청은 OTP를 소비하지 않고 AUT0303을 던진다")
    void cancel_onExecutionDate_throwsCannotTerminateOnExecutionDate_withoutConsumingOtp() {
        AutoTransfer dueToday = AutoTransfer.reconstitute(
                10L, 1L, 2L, "110987654321", "홍길동",
                10_000L, 1, 15,
                LocalDate.now().minusMonths(1), LocalDate.now().plusMonths(12), LocalDate.now(),
                "내메모", "받는메모", AutoTransferStatus.NORMAL,
                LocalDateTime.now(), null, LocalDateTime.now(), 0L);
        when(autoTransferPersistencePort.findById(10L)).thenReturn(Optional.of(dueToday));
        when(clock.withZone(any())).thenReturn(Clock.systemUTC());

        assertThatThrownBy(() -> autoTransferCommandService.cancel(10L, validCancelCommandBuilder().build()))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(AutoTransferErrorCode.CANNOT_TERMINATE_ON_EXECUTION_DATE));

        verify(authTokenVerificationPort, never()).verify(any(), any(), any());
        verify(autoTransferOtpVerificationPort, never()).verifyCancelAndConsume(any(), any(), any());
        verify(autoTransferPersistencePort, never()).save(any());
    }
}