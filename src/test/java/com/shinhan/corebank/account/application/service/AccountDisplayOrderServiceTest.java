package com.shinhan.corebank.account.application.service;

import com.shinhan.corebank.account.application.port.in.AccountDisplayOrderCommand;
import com.shinhan.corebank.account.application.port.in.AccountDisplayOrderResult;
import com.shinhan.corebank.account.application.port.out.AccountPersistencePort;
import com.shinhan.corebank.account.domain.Account;
import com.shinhan.corebank.account.domain.AccountStatus;
import com.shinhan.corebank.account.domain.AccountType;
import com.shinhan.corebank.account.domain.exception.AccountErrorCode;
import com.shinhan.corebank.common.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AccountDisplayOrderServiceTest {

    private static final Long CUSTOMER_ID = 1L;

    private static final String PASSWORD_HASH =
            "$2y$10$1NOtaTsHuD0rdffA3ReFKO5S0J4bHlVES6okQMYubUd0OuVFfMZXa";

    @Mock
    private AccountPersistencePort accountPersistencePort;

    @InjectMocks
    private AccountDisplayOrderService accountDisplayOrderService;

    @Test
    @DisplayName("요청된 계좌 순서대로 표시순서를 저장한다")
    void saveDisplayOrder() {
        // given
        Account account101 = createAccount(
                101L,
                "088100000101",
                LocalDateTime.of(
                        2026, 8, 1, 10, 0
                ),
                null
        );

        Account account102 = createAccount(
                102L,
                "088100000102",
                LocalDateTime.of(
                        2026, 8, 2, 10, 0
                ),
                null
        );

        Account account103 = createAccount(
                103L,
                "088100000103",
                LocalDateTime.of(
                        2026, 8, 3, 10, 0
                ),
                null
        );

        when(
                accountPersistencePort.findAllByCustomerId(
                        CUSTOMER_ID
                )
        ).thenReturn(
                List.of(
                        account101,
                        account102,
                        account103
                )
        );

        AccountDisplayOrderCommand command =
                new AccountDisplayOrderCommand(
                        CUSTOMER_ID,
                        List.of(
                                103L,
                                101L,
                                102L
                        )
                );

        // when
        AccountDisplayOrderResult result =
                accountDisplayOrderService
                        .saveDisplayOrder(command);

        // then
        assertThat(account103.getDisplayOrder())
                .isEqualTo(1);

        assertThat(account101.getDisplayOrder())
                .isEqualTo(2);

        assertThat(account102.getDisplayOrder())
                .isEqualTo(3);

        assertThat(result.accountIds())
                .containsExactly(
                        103L,
                        101L,
                        102L
                );

        verify(accountPersistencePort)
                .save(account101);

        verify(accountPersistencePort)
                .save(account102);

        verify(accountPersistencePort)
                .save(account103);
    }

    @Test
    @DisplayName("계좌 ID가 중복되면 ACC0002를 발생시킨다")
    void rejectDuplicateAccountIds() {
        // given
        Account account101 =
                createAccount(
                        101L,
                        "088100000101",
                        LocalDateTime.of(
                                2026, 8, 1, 10, 0
                        ),
                        null
                );

        Account account102 =
                createAccount(
                        102L,
                        "088100000102",
                        LocalDateTime.of(
                                2026, 8, 2, 10, 0
                        ),
                        null
                );

        when(
                accountPersistencePort.findAllByCustomerId(
                        CUSTOMER_ID
                )
        ).thenReturn(
                List.of(
                        account101,
                        account102
                )
        );

        AccountDisplayOrderCommand command =
                new AccountDisplayOrderCommand(
                        CUSTOMER_ID,
                        List.of(
                                101L,
                                101L
                        )
                );

        // when
        BusinessException exception =
                catchThrowableOfType(
                        BusinessException.class,
                        () ->
                                accountDisplayOrderService
                                        .saveDisplayOrder(
                                                command
                                        )
                );

        // then
        assertThat(exception.getErrorCode())
                .isEqualTo(
                        AccountErrorCode.INVALID_DISPLAY_ORDER
                );

        verify(
                accountPersistencePort,
                never()
        ).save(
                org.mockito.ArgumentMatchers.any()
        );
    }

    @Test
    @DisplayName("본인 소유가 아닌 계좌가 포함되면 ACC0201을 발생시킨다")
    void rejectUnknownAccount() {
        // given
        Account account101 =
                createAccount(
                        101L,
                        "088100000101",
                        LocalDateTime.of(
                                2026, 8, 1, 10, 0
                        ),
                        null
                );

        Account account102 =
                createAccount(
                        102L,
                        "088100000102",
                        LocalDateTime.of(
                                2026, 8, 2, 10, 0
                        ),
                        null
                );

        when(
                accountPersistencePort.findAllByCustomerId(
                        CUSTOMER_ID
                )
        ).thenReturn(
                List.of(
                        account101,
                        account102
                )
        );

        AccountDisplayOrderCommand command =
                new AccountDisplayOrderCommand(
                        CUSTOMER_ID,
                        List.of(
                                101L,
                                999L
                        )
                );

        // when
        BusinessException exception =
                catchThrowableOfType(
                        BusinessException.class,
                        () ->
                                accountDisplayOrderService
                                        .saveDisplayOrder(
                                                command
                                        )
                );

        // then
        assertThat(exception.getErrorCode())
                .isEqualTo(
                        AccountErrorCode
                                .ACCOUNT_NOT_FOUND_OR_FORBIDDEN
                );

        verify(
                accountPersistencePort,
                never()
        ).save(
                org.mockito.ArgumentMatchers.any()
        );
    }

    @Test
    @DisplayName("본인 계좌가 누락되면 ACC0002를 발생시킨다")
    void rejectMissingAccount() {
        // given
        Account account101 =
                createAccount(
                        101L,
                        "088100000101",
                        LocalDateTime.of(
                                2026, 8, 1, 10, 0
                        ),
                        null
                );

        Account account102 =
                createAccount(
                        102L,
                        "088100000102",
                        LocalDateTime.of(
                                2026, 8, 2, 10, 0
                        ),
                        null
                );

        Account account103 =
                createAccount(
                        103L,
                        "088100000103",
                        LocalDateTime.of(
                                2026, 8, 3, 10, 0
                        ),
                        null
                );

        when(
                accountPersistencePort.findAllByCustomerId(
                        CUSTOMER_ID
                )
        ).thenReturn(
                List.of(
                        account101,
                        account102,
                        account103
                )
        );

        AccountDisplayOrderCommand command =
                new AccountDisplayOrderCommand(
                        CUSTOMER_ID,
                        List.of(
                                101L,
                                102L
                        )
                );

        // when
        BusinessException exception =
                catchThrowableOfType(
                        BusinessException.class,
                        () ->
                                accountDisplayOrderService
                                        .saveDisplayOrder(
                                                command
                                        )
                );

        // then
        assertThat(exception.getErrorCode())
                .isEqualTo(
                        AccountErrorCode.INVALID_DISPLAY_ORDER
                );

        verify(
                accountPersistencePort,
                never()
        ).save(
                org.mockito.ArgumentMatchers.any()
        );
    }

    @Test
    @DisplayName("표시순서를 초기화하면 기본순서로 계좌 ID를 반환한다")
    void resetDisplayOrder() {
        // given
        Account account103 =
                createAccount(
                        103L,
                        "088100000103",
                        LocalDateTime.of(
                                2026, 8, 2, 9, 0
                        ),
                        1
                );

        Account account102 =
                createAccount(
                        102L,
                        "088100000102",
                        LocalDateTime.of(
                                2026, 8, 1, 15, 0
                        ),
                        2
                );

        Account account101 =
                createAccount(
                        101L,
                        "088100000101",
                        LocalDateTime.of(
                                2026, 8, 1, 10, 0
                        ),
                        3
                );

        when(
                accountPersistencePort.findAllByCustomerId(
                        CUSTOMER_ID
                )
        ).thenReturn(
                List.of(
                        account103,
                        account102,
                        account101
                )
        );

        // when
        AccountDisplayOrderResult result =
                accountDisplayOrderService
                        .resetDisplayOrder(
                                CUSTOMER_ID
                        );

        // then
        assertThat(account101.getDisplayOrder())
                .isNull();

        assertThat(account102.getDisplayOrder())
                .isNull();

        assertThat(account103.getDisplayOrder())
                .isNull();

        assertThat(result.accountIds())
                .containsExactly(
                        101L,
                        102L,
                        103L
                );

        verify(accountPersistencePort)
                .save(account101);

        verify(accountPersistencePort)
                .save(account102);

        verify(accountPersistencePort)
                .save(account103);
    }

    private Account createAccount(
            Long accountId,
            String accountNumber,
            LocalDateTime openedDate,
            Integer displayOrder
    ) {
        return Account.reconstitute(
                accountId,
                accountNumber,
                CUSTOMER_ID,
                null,
                AccountType.DEMAND_DEPOSIT,
                0L,
                AccountStatus.ACTIVE,
                PASSWORD_HASH,
                0,
                false,
                null,
                displayOrder,
                false,
                null,
                openedDate,
                null,
                null,
                null,
                0L,
                openedDate,
                openedDate
        );
    }
}