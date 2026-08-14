package com.shinhan.corebank.account.adapter.in.web;

import com.shinhan.corebank.IntegrationTestSupport;
import com.shinhan.corebank.account.application.port.out.AccountPersistencePort;
import com.shinhan.corebank.account.domain.Account;
import com.shinhan.corebank.account.domain.AccountStatus;
import com.shinhan.corebank.account.domain.AccountType;
import com.shinhan.corebank.account.support.CustomerTestFixture;
import com.shinhan.corebank.auth.api.AuthenticatedCustomer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
@Transactional
class AccountControllerTest extends IntegrationTestSupport {

    private static final String PASSWORD_HASH =
            "$2a$10$34abEWY4uXLwTEnT5hNow.603a5rWofFx7Bnj59agU.PsESK0v/Yq";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AccountPersistencePort accountPersistencePort;

    @Autowired
    private CustomerTestFixture customerTestFixture;

    @Test
    @DisplayName("로그인 고객의 전체 계좌를 조회한다")
    void getAccountsReturnsCurrentCustomersAccounts()
            throws Exception {

        // given
        Long customerId =
                customerTestFixture.createCustomer();

        Long otherCustomerId =
                customerTestFixture.createCustomer();

        Account myAccount = createDemandDepositAccount(
                customerId,
                "088100000011",
                1_500_000L,
                null,
                AccountStatus.ACTIVE,
                true
        );

        Account otherAccount = createDemandDepositAccount(
                otherCustomerId,
                "088100000012",
                9_999_999L,
                "타인 계좌",
                AccountStatus.ACTIVE,
                true
        );

        accountPersistencePort.save(myAccount);
        accountPersistencePort.save(otherAccount);

        // when & then
        mockMvc.perform(
                        get("/accounts")
                                .with(authentication(
                                        authenticationOf(customerId)
                                ))
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code")
                        .value("0000"))
                .andExpect(jsonPath("$.message")
                        .value("정상 처리되었습니다."))

                .andExpect(jsonPath("$.data.asOf")
                        .isNotEmpty())

                .andExpect(jsonPath("$.data.totalAssets")
                        .value(1_500_000L))

                .andExpect(jsonPath("$.data.items.length()")
                        .value(1))

                .andExpect(jsonPath(
                        "$.data.items[0].groupCode"
                ).value("DEMAND_DEPOSIT"))

                .andExpect(jsonPath(
                        "$.data.items[0].groupName"
                ).value("입출금계좌"))

                .andExpect(jsonPath(
                        "$.data.items[0].groupTotalBalance"
                ).value(1_500_000L))

                .andExpect(jsonPath(
                        "$.data.items[0].accounts.length()"
                ).value(1))

                .andExpect(jsonPath(
                        "$.data.items[0].accounts[0].accountName"
                ).value("입출금통장"))

                .andExpect(jsonPath(
                        "$.data.items[0].accounts[0].accountNumber"
                ).value("088100000011"))

                .andExpect(jsonPath(
                        "$.data.items[0].accounts[0].accountType"
                ).value("DEMAND_DEPOSIT"))

                .andExpect(jsonPath(
                        "$.data.items[0].accounts[0].balance"
                ).value(1_500_000L))

                .andExpect(jsonPath(
                        "$.data.items[0].accounts[0].status"
                ).value("ACTIVE"))

                .andExpect(jsonPath(
                        "$.data.items[0].accounts[0].openedDate"
                ).value("2026-08-01"))

                .andExpect(jsonPath(
                        "$.data.items[0].accounts[0].lastTransactionAt"
                ).value("2026-08-10T15:00:00"))

                .andExpect(jsonPath(
                        "$.data.items[0].accounts[0].transferEnabled"
                ).value(true));
    }

    @Test
    @DisplayName("보유 계좌가 없으면 전체 자산 0과 빈 목록을 반환한다")
    void getAccountsReturnsEmptyItemsWhenNoAccountsExist()
            throws Exception {

        // given
        Long customerId =
                customerTestFixture.createCustomer();

        // when & then
        mockMvc.perform(
                        get("/accounts")
                                .with(authentication(
                                        authenticationOf(customerId)
                                ))
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code")
                        .value("0000"))
                .andExpect(jsonPath("$.data.totalAssets")
                        .value(0))
                .andExpect(jsonPath("$.data.items")
                        .isArray())
                .andExpect(jsonPath("$.data.items")
                        .isEmpty());
    }

    @Test
    @DisplayName("인증 없이 전체 계좌 조회를 요청하면 401을 반환한다")
    void getAccountsWithoutAuthenticationReturnsUnauthorized()
            throws Exception {

        mockMvc.perform(get("/accounts"))
                .andExpect(status().isUnauthorized());
    }

    private Account createDemandDepositAccount(
            Long customerId,
            String accountNumber,
            long balance,
            String alias,
            AccountStatus status,
            boolean withdrawalRegistered
    ) {
        LocalDateTime openedDate =
                LocalDateTime.of(
                        2026, 8, 1, 10, 0
                );

        LocalDateTime withdrawalRegisteredAt =
                withdrawalRegistered
                        ? LocalDateTime.of(
                                2026, 8, 2, 10, 0
                        )
                        : null;

        return Account.reconstitute(
                null,
                accountNumber,
                customerId,
                null,
                AccountType.DEMAND_DEPOSIT,
                balance,
                status,
                PASSWORD_HASH,
                0,
                false,
                alias,
                null,
                withdrawalRegistered,
                withdrawalRegisteredAt,
                openedDate,
                null,
                null,
                LocalDateTime.of(
                        2026, 8, 10, 15, 0
                ),
                null,
                null,
                null
        );
    }

    private UsernamePasswordAuthenticationToken authenticationOf(
            Long customerId
    ) {
        AuthenticatedCustomer customer =
                new AuthenticatedCustomer(
                        customerId,
                        "user" + customerId,
                        "테스터"
                );

        return UsernamePasswordAuthenticationToken
                .authenticated(
                        customer,
                        null,
                        AuthorityUtils.createAuthorityList(
                                "ROLE_CUSTOMER"
                        )
                );
    }
}