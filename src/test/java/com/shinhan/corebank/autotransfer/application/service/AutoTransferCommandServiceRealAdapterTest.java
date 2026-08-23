package com.shinhan.corebank.autotransfer.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.shinhan.corebank.IntegrationTestSupport;
import com.shinhan.corebank.autotransfer.adapter.out.account.AccountLookupJpaRepository;
import com.shinhan.corebank.autotransfer.adapter.out.account.AccountStatusAdapter;
import com.shinhan.corebank.autotransfer.application.port.in.AutoTransferRegisterCommand;
import com.shinhan.corebank.autotransfer.application.port.out.AuthTokenVerificationPort;
import com.shinhan.corebank.autotransfer.application.port.out.AutoTransferOtpVerificationPort;
import com.shinhan.corebank.autotransfer.application.port.out.AutoTransferPersistencePort;
import com.shinhan.corebank.autotransfer.application.port.out.TransferLimitPort;
import com.shinhan.corebank.autotransfer.domain.AutoTransfer;
import com.shinhan.corebank.autotransfer.domain.AutoTransferErrorCode;
import com.shinhan.corebank.common.audit.AuditLogService;
import com.shinhan.corebank.common.exception.BusinessException;
import java.time.Clock;
import java.time.LocalDate;
import java.util.concurrent.atomic.AtomicLong;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

// AccountStatusAdapter는 @Profile("prod")라 test 프로필의 정상적인 스프링 주입 경로로는
// AutoTransferCommandService에 안 붙는다. 그래서 여기서는 real adapter를 직접 조립해
// AutoTransferCommandService.register()가 real adapter와 함께 실제로 동작하는지(=#180 DoD)
// 검증한다. 나머지 포트는 Mockito mock으로 대체한다.
@Transactional
class AutoTransferCommandServiceRealAdapterTest extends IntegrationTestSupport {

    @Autowired
    AccountLookupJpaRepository accountLookupJpaRepository;

    @Autowired
    EntityManager entityManager;

    private static final AtomicLong CUSTOMER_SEQ = new AtomicLong();
    private static final AtomicLong ACCOUNT_SEQ = new AtomicLong();

    private AutoTransferCommandService commandService;
    private AutoTransferPersistencePort autoTransferPersistencePort;
    private TransferLimitPort transferLimitPort;

    @BeforeEach
    void setUp() {
        AccountStatusAdapter realAccountStatusAdapter = new AccountStatusAdapter(accountLookupJpaRepository);
        autoTransferPersistencePort = mock(AutoTransferPersistencePort.class);
        AuthTokenVerificationPort authTokenVerificationPort = mock(AuthTokenVerificationPort.class);
        AutoTransferOtpVerificationPort autoTransferOtpVerificationPort = mock(AutoTransferOtpVerificationPort.class);
        transferLimitPort = mock(TransferLimitPort.class);
        when(transferLimitPort.findOneTimeLimit(anyLong())).thenReturn(10_000_000L);
        AuditLogService auditLogService = mock(AuditLogService.class);

        commandService = new AutoTransferCommandService(
                autoTransferPersistencePort, authTokenVerificationPort, autoTransferOtpVerificationPort, realAccountStatusAdapter,
                transferLimitPort, auditLogService, Clock.systemDefaultZone());
    }

    @Test
    @DisplayName("출금계좌가 SUSPENDED면 real adapter가 걸러내서 AUT0202를 던진다")
    void register_suspendedWithdrawalAccount_blockedByRealAdapter() {
        Long customerId = insertCustomer();
        Long suspendedAccountId = insertAccount(customerId, "SUSPENDED");
        Long depositCustomerId = insertCustomer();
        insertAccount(depositCustomerId, "ACTIVE");
        String depositAccountNumber = lastInsertedAccountNumber;

        AutoTransferRegisterCommand command = registerCommandBuilder(customerId, suspendedAccountId, depositAccountNumber).build();

        assertThatThrownBy(() -> commandService.register(command))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(AutoTransferErrorCode.ACCOUNT_NOT_ACCESSIBLE));
    }

    @Test
    @DisplayName("입금계좌가 실제로 존재하지 않으면 real adapter가 걸러내서 AUT0202를 던진다")
    void register_nonExistentDepositAccount_blockedByRealAdapter() {
        Long customerId = insertCustomer();
        Long withdrawalAccountId = insertAccount(customerId, "ACTIVE");

        AutoTransferRegisterCommand command = registerCommandBuilder(customerId, withdrawalAccountId, "999999999999").build();

        assertThatThrownBy(() -> commandService.register(command))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(AutoTransferErrorCode.ACCOUNT_NOT_ACCESSIBLE));
    }

    @Test
    @DisplayName("출금계좌로 등록되지 않은 계좌면(withdrawal_registered=FALSE) real adapter가 걸러내서 AUT0202를 던진다")
    void register_withdrawalNotRegisteredAccount_blockedByRealAdapter() {
        Long customerId = insertCustomer();
        Long notRegisteredAccountId = insertAccountWithoutWithdrawalRegistration(customerId);
        Long depositCustomerId = insertCustomer();
        insertAccount(depositCustomerId, "ACTIVE");
        String depositAccountNumber = lastInsertedAccountNumber;

        AutoTransferRegisterCommand command = registerCommandBuilder(customerId, notRegisteredAccountId, depositAccountNumber).build();

        assertThatThrownBy(() -> commandService.register(command))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(AutoTransferErrorCode.ACCOUNT_NOT_ACCESSIBLE));
    }

    @Test
    @DisplayName("출금계좌 ACTIVE + 입금계좌 실존하면 real adapter를 통과해서 등록된다")
    void register_activeAccountsBothExist_passesRealAdapter() {
        Long customerId = insertCustomer();
        Long withdrawalAccountId = insertAccount(customerId, "ACTIVE");
        Long depositCustomerId = insertCustomer();
        insertAccount(depositCustomerId, "ACTIVE");
        String depositAccountNumber = lastInsertedAccountNumber;
        when(autoTransferPersistencePort.existsActiveDuplicate(any(), any(), anyInt())).thenReturn(false);
        when(autoTransferPersistencePort.save(any())).thenAnswer(invocation -> {
            AutoTransfer arg = invocation.getArgument(0);
            return AutoTransfer.reconstitute(
                    900L, arg.getCustomerId(), arg.getWithdrawalAccountId(), arg.getDepositAccountNumber(), arg.getPayeeName(),
                    arg.getAmount(), arg.getCycleMonths(), arg.getTransferDay(), arg.getStartDate(), arg.getEndDate(),
                    arg.getNextExecutionDate(), arg.getMyPassbookMemo(), arg.getRecipientPassbookMemo(), arg.getStatus(),
                    arg.getRegisteredAt(), arg.getTerminatedAt(), arg.getUpdatedAt(), arg.getVersion());
        });

        AutoTransferRegisterCommand command = registerCommandBuilder(customerId, withdrawalAccountId, depositAccountNumber).build();

        assertThat(commandService.register(command)).isNotNull();
    }

    private AutoTransferRegisterCommand.AutoTransferRegisterCommandBuilder registerCommandBuilder(
            Long customerId, Long withdrawalAccountId, String depositAccountNumber) {
        return AutoTransferRegisterCommand.builder()
                .customerId(customerId)
                .withdrawalAccountId(withdrawalAccountId)
                .depositAccountNumber(depositAccountNumber)
                .payeeName("홍길동")
                .amount(10_000L)
                .cycleMonths(1)
                .transferDay(15)
                .startDate(LocalDate.now().plusDays(10))
                .endDate(LocalDate.now().plusMonths(12))
                .accountPasswordAuthToken("token")
                .otpAuthToken("otp-token")
                .requestIp("127.0.0.1");
    }

    private String lastInsertedAccountNumber;

    private Long insertCustomer() {
        long seq = CUSTOMER_SEQ.incrementAndGet();
        entityManager.createNativeQuery(
                        "INSERT INTO customer (user_id, password_hash, user_name, birth_date, email, phone_number, joined_at, created_at, updated_at) "
                                + "VALUES (:userId, 'x', '홍길동', '1990-01-01', :email, '01012345678', NOW(), NOW(), NOW())")
                .setParameter("userId", "u" + seq)
                .setParameter("email", "test" + seq + "@test.com")
                .executeUpdate();
        return ((Number) entityManager.createNativeQuery("SELECT LAST_INSERT_ID()").getSingleResult()).longValue();
    }

    // withdrawal_registered=TRUE로 채운다 - 등록 시 출금계좌 등록 여부 검증(#234)이 이 값을 확인하므로,
    // 이 테스트들의 원래 의도(SUSPENDED/입금계좌 미존재로 걸러지는지)를 그대로 검증하려면 출금계좌 등록 자체는 정상이어야 한다
    private Long insertAccount(Long customerId, String status) {
        String accountNumber = String.format("%012d", ACCOUNT_SEQ.incrementAndGet());
        entityManager.createNativeQuery(
                        "INSERT INTO account (account_number, customer_id, account_type, status, password_hash, "
                                + "withdrawal_registered, withdrawal_registered_at, opened_date, created_at, updated_at) "
                                + "VALUES (:accountNumber, :customerId, 'DEMAND_DEPOSIT', :status, 'x', TRUE, NOW(), NOW(), NOW(), NOW())")
                .setParameter("accountNumber", accountNumber)
                .setParameter("customerId", customerId)
                .setParameter("status", status)
                .executeUpdate();
        lastInsertedAccountNumber = accountNumber;
        return ((Number) entityManager.createNativeQuery("SELECT LAST_INSERT_ID()").getSingleResult()).longValue();
    }

    private Long insertAccountWithoutWithdrawalRegistration(Long customerId) {
        String accountNumber = String.format("%012d", ACCOUNT_SEQ.incrementAndGet());
        entityManager.createNativeQuery(
                        "INSERT INTO account (account_number, customer_id, account_type, status, password_hash, "
                                + "withdrawal_registered, opened_date, created_at, updated_at) "
                                + "VALUES (:accountNumber, :customerId, 'DEMAND_DEPOSIT', 'ACTIVE', 'x', FALSE, NOW(), NOW(), NOW())")
                .setParameter("accountNumber", accountNumber)
                .setParameter("customerId", customerId)
                .executeUpdate();
        return ((Number) entityManager.createNativeQuery("SELECT LAST_INSERT_ID()").getSingleResult()).longValue();
    }
}
