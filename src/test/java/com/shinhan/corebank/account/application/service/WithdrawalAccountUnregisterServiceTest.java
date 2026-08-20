package com.shinhan.corebank.account.application.service;

import com.shinhan.corebank.account.application.port.in.WithdrawalAccountUnregisterCommand;
import com.shinhan.corebank.account.application.port.in.WithdrawalAccountUnregisterResult;
import com.shinhan.corebank.account.application.port.out.AccountPersistencePort;
import com.shinhan.corebank.account.application.port.out.AutoTransferUsageQueryPort;
import com.shinhan.corebank.account.application.port.out.ScheduledTransferUsageQueryPort;
import com.shinhan.corebank.account.domain.Account;
import com.shinhan.corebank.account.domain.AccountStatus;
import com.shinhan.corebank.account.domain.AccountType;
import com.shinhan.corebank.account.domain.exception.AccountErrorCode;
import com.shinhan.corebank.common.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WithdrawalAccountUnregisterServiceTest {

    private static final Long CUSTOMER_ID = 1L;
    private static final Long ACCOUNT_ID = 10L;

    private static final String PASSWORD_HASH =
            "$2a$10$34abEWY4uXLwTEnT5hNow.603a5rWofFx7Bnj59agU.PsESK0v/Yq";

    private static final LocalDateTime REGISTERED_AT =
            LocalDateTime.of(
                    2026,
                    8,
                    19,
                    14,
                    30
            );

    @Mock
    private AccountPersistencePort accountPersistencePort;

    @Mock
    private ScheduledTransferUsageQueryPort
            scheduledTransferUsageQueryPort;

    @Mock
    private AutoTransferUsageQueryPort
            autoTransferUsageQueryPort;

    private WithdrawalAccountUnregisterService service;

    @BeforeEach
    void setUp() {
        service =
                new WithdrawalAccountUnregisterService(
                        accountPersistencePort,
                        scheduledTransferUsageQueryPort,
                        autoTransferUsageQueryPort
                );
    }

    @Test
    @DisplayName("등록된 출금계좌를 삭제한다")
    void unregisterWithdrawalAccount() {
        // given
        Account account =
                createAccount(
                        true,
                        REGISTERED_AT
                );

        when(
                accountPersistencePort
                        .findByAccountIdAndCustomerId(
                                ACCOUNT_ID,
                                CUSTOMER_ID
                        )
        ).thenReturn(
                Optional.of(account)
        );

        when(
                scheduledTransferUsageQueryPort
                        .existsUsingWithdrawalAccount(
                                ACCOUNT_ID
                        )
        ).thenReturn(false);

        when(
                autoTransferUsageQueryPort
                        .existsUsingWithdrawalAccount(
                                ACCOUNT_ID
                        )
        ).thenReturn(false);

        when(
                accountPersistencePort
                        .save(any(Account.class))
        ).thenAnswer(
                invocation ->
                        invocation.getArgument(0)
        );

        // when
        WithdrawalAccountUnregisterResult result =
                service.unregister(
                        createCommand()
                );

        // then
        assertThat(result.accountId())
                .isEqualTo(ACCOUNT_ID);

        assertThat(
                result.withdrawalAccountRegistered()
        ).isFalse();

        assertThat(
                account.isWithdrawalRegistered()
        ).isFalse();

        assertThat(
                account.getWithdrawalRegisteredAt()
        ).isNull();

        verify(
                scheduledTransferUsageQueryPort
        ).existsUsingWithdrawalAccount(
                ACCOUNT_ID
        );

        verify(
                autoTransferUsageQueryPort
        ).existsUsingWithdrawalAccount(
                ACCOUNT_ID
        );

        verify(
                accountPersistencePort
        ).save(account);
    }

    @Test
    @DisplayName("이미 미등록 상태인 계좌는 성공으로 처리하고 다시 저장하지 않는다")
    void returnSuccessWhenAlreadyUnregistered() {
        // given
        Account account =
                createAccount(
                        false,
                        null
                );

        when(
                accountPersistencePort
                        .findByAccountIdAndCustomerId(
                                ACCOUNT_ID,
                                CUSTOMER_ID
                        )
        ).thenReturn(
                Optional.of(account)
        );

        // when
        WithdrawalAccountUnregisterResult result =
                service.unregister(
                        createCommand()
                );

        // then
        assertThat(result.accountId())
                .isEqualTo(ACCOUNT_ID);

        assertThat(
                result.withdrawalAccountRegistered()
        ).isFalse();

        verifyNoInteractions(
                scheduledTransferUsageQueryPort
        );

        verifyNoInteractions(
                autoTransferUsageQueryPort
        );

        verify(
                accountPersistencePort,
                never()
        ).save(any());
    }

    @Test
    @DisplayName("존재하지 않거나 접근할 수 없는 계좌는 ACC0201을 발생시킨다")
    void rejectNotFoundOrForbiddenAccount() {
        // given
        when(
                accountPersistencePort
                        .findByAccountIdAndCustomerId(
                                ACCOUNT_ID,
                                CUSTOMER_ID
                        )
        ).thenReturn(
                Optional.empty()
        );

        // when
        BusinessException exception =
                catchThrowableOfType(
                        () ->
                                service.unregister(
                                        createCommand()
                                ),
                        BusinessException.class
                );

        // then
        assertThat(
                exception.getErrorCode()
        ).isEqualTo(
                AccountErrorCode
                        .ACCOUNT_NOT_FOUND_OR_FORBIDDEN
        );

        verifyNoInteractions(
                scheduledTransferUsageQueryPort
        );

        verifyNoInteractions(
                autoTransferUsageQueryPort
        );

        verify(
                accountPersistencePort,
                never()
        ).save(any());
    }

    @Test
    @DisplayName("대기 중인 예약이체가 있으면 출금계좌를 삭제할 수 없다")
    void rejectWhenScheduledTransferExists() {
        // given
        Account account =
                createAccount(
                        true,
                        REGISTERED_AT
                );

        when(
                accountPersistencePort
                        .findByAccountIdAndCustomerId(
                                ACCOUNT_ID,
                                CUSTOMER_ID
                        )
        ).thenReturn(
                Optional.of(account)
        );

        when(
                scheduledTransferUsageQueryPort
                        .existsUsingWithdrawalAccount(
                                ACCOUNT_ID
                        )
        ).thenReturn(true);

        // when
        BusinessException exception =
                catchThrowableOfType(
                        () ->
                                service.unregister(
                                        createCommand()
                                ),
                        BusinessException.class
                );

        // then
        assertThat(
                exception.getErrorCode()
        ).isEqualTo(
                AccountErrorCode
                        .WITHDRAWAL_ACCOUNT_UNREGISTRATION_RESTRICTED
        );

        /*
         * 예약이체 검사에서 차단됐으므로
         * 자동이체는 조회할 필요가 없다.
         */
        verifyNoInteractions(
                autoTransferUsageQueryPort
        );

        verify(
                accountPersistencePort,
                never()
        ).save(any());

        /*
         * 상태 변경보다 사용 여부 검사를 먼저 하므로
         * 도메인 객체도 아직 등록 상태다.
         */
        assertThat(
                account.isWithdrawalRegistered()
        ).isTrue();

        assertThat(
                account.getWithdrawalRegisteredAt()
        ).isEqualTo(REGISTERED_AT);
    }

    @Test
    @DisplayName("정상 상태 자동이체가 있으면 출금계좌를 삭제할 수 없다")
    void rejectWhenAutoTransferExists() {
        // given
        Account account =
                createAccount(
                        true,
                        REGISTERED_AT
                );

        when(
                accountPersistencePort
                        .findByAccountIdAndCustomerId(
                                ACCOUNT_ID,
                                CUSTOMER_ID
                        )
        ).thenReturn(
                Optional.of(account)
        );

        when(
                scheduledTransferUsageQueryPort
                        .existsUsingWithdrawalAccount(
                                ACCOUNT_ID
                        )
        ).thenReturn(false);

        when(
                autoTransferUsageQueryPort
                        .existsUsingWithdrawalAccount(
                                ACCOUNT_ID
                        )
        ).thenReturn(true);

        // when
        BusinessException exception =
                catchThrowableOfType(
                        () ->
                                service.unregister(
                                        createCommand()
                                ),
                        BusinessException.class
                );

        // then
        assertThat(
                exception.getErrorCode()
        ).isEqualTo(
                AccountErrorCode
                        .WITHDRAWAL_ACCOUNT_UNREGISTRATION_RESTRICTED
        );

        verify(
                accountPersistencePort,
                never()
        ).save(any());

        assertThat(
                account.isWithdrawalRegistered()
        ).isTrue();

        assertThat(
                account.getWithdrawalRegisteredAt()
        ).isEqualTo(REGISTERED_AT);
    }

    @Test
    @DisplayName("예약이체 사용 여부 조회에 실패하면 계좌 상태를 변경하거나 저장하지 않는다")
    void doNotChangeAccountWhenScheduledTransferLookupFails() {
        // given
        Account account =
                createAccount(
                        true,
                        REGISTERED_AT
                );

        when(
                accountPersistencePort
                        .findByAccountIdAndCustomerId(
                                ACCOUNT_ID,
                                CUSTOMER_ID
                        )
        ).thenReturn(
                Optional.of(account)
        );

        when(
                scheduledTransferUsageQueryPort
                        .existsUsingWithdrawalAccount(
                                ACCOUNT_ID
                        )
        ).thenThrow(
                new RuntimeException(
                        "예약이체 조회 실패"
                )
        );

        // when & then
        assertThatThrownBy(
                () ->
                        service.unregister(
                                createCommand()
                        )
        )
                .isInstanceOf(
                        RuntimeException.class
                )
                .hasMessage(
                        "예약이체 조회 실패"
                );

        verifyNoInteractions(
                autoTransferUsageQueryPort
        );

        verify(
                accountPersistencePort,
                never()
        ).save(any());

        assertThat(
                account.isWithdrawalRegistered()
        ).isTrue();

        assertThat(
                account.getWithdrawalRegisteredAt()
        ).isEqualTo(REGISTERED_AT);
    }

    @Test
    @DisplayName("자동이체 사용 여부 조회에 실패하면 계좌 상태를 변경하거나 저장하지 않는다")
    void doNotChangeAccountWhenAutoTransferLookupFails() {
        // given
        Account account =
                createAccount(
                        true,
                        REGISTERED_AT
                );

        when(
                accountPersistencePort
                        .findByAccountIdAndCustomerId(
                                ACCOUNT_ID,
                                CUSTOMER_ID
                        )
        ).thenReturn(
                Optional.of(account)
        );

        when(
                scheduledTransferUsageQueryPort
                        .existsUsingWithdrawalAccount(
                                ACCOUNT_ID
                        )
        ).thenReturn(false);

        when(
                autoTransferUsageQueryPort
                        .existsUsingWithdrawalAccount(
                                ACCOUNT_ID
                        )
        ).thenThrow(
                new RuntimeException(
                        "자동이체 조회 실패"
                )
        );

        // when & then
        assertThatThrownBy(
                () ->
                        service.unregister(
                                createCommand()
                        )
        )
                .isInstanceOf(
                        RuntimeException.class
                )
                .hasMessage(
                        "자동이체 조회 실패"
                );

        verify(
                accountPersistencePort,
                never()
        ).save(any());

        assertThat(
                account.isWithdrawalRegistered()
        ).isTrue();

        assertThat(
                account.getWithdrawalRegisteredAt()
        ).isEqualTo(REGISTERED_AT);
    }

    private WithdrawalAccountUnregisterCommand
    createCommand() {
        return new WithdrawalAccountUnregisterCommand(
                CUSTOMER_ID,
                ACCOUNT_ID
        );
    }

    private Account createAccount(
            boolean withdrawalRegistered,
            LocalDateTime withdrawalRegisteredAt
    ) {
        return Account.reconstitute(
                ACCOUNT_ID,
                "088100000001",
                CUSTOMER_ID,
                null,
                AccountType.DEMAND_DEPOSIT,
                1_000_000L,
                AccountStatus.ACTIVE,
                PASSWORD_HASH,
                0,
                false,
                null,
                null,
                withdrawalRegistered,
                withdrawalRegisteredAt,
                LocalDateTime.of(
                        2026,
                        8,
                        1,
                        10,
                        0
                ),
                null,
                null,
                LocalDateTime.of(
                        2026,
                        8,
                        10,
                        15,
                        0
                ),
                0L,
                LocalDateTime.of(
                        2026,
                        8,
                        1,
                        10,
                        0
                ),
                LocalDateTime.of(
                        2026,
                        8,
                        10,
                        15,
                        0
                )
        );
    }
}
