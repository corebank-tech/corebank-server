package com.shinhan.corebank.account.adapter.in.web;

import com.shinhan.corebank.IntegrationTestSupport;
import com.shinhan.corebank.account.application.port.out.AccountPersistencePort;
import com.shinhan.corebank.account.domain.Account;
import com.shinhan.corebank.account.domain.AccountType;
import com.shinhan.corebank.account.support.CustomerTestFixture;
import com.shinhan.corebank.auth.api.AuthenticatedCustomer;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
@Transactional
class AccountPreferenceControllerTest
        extends IntegrationTestSupport {

    private static final String PASSWORD_HASH =
            "$2a$10$34abEWY4uXLwTEnT5hNow.603a5rWofFx7Bnj59agU.PsESK0v/Yq";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AccountPersistencePort accountPersistencePort;

    @Autowired
    private CustomerTestFixture customerTestFixture;

    @Autowired
    private EntityManager entityManager;

    @Test
    @DisplayName("본인 소유 계좌의 표시순서를 저장한다")
    void saveDisplayOrder() throws Exception {
        Long customerId =
                customerTestFixture.createCustomer();

        Account account1 =
                createAccount(
                        customerId,
                        "088100000061"
                );

        Account account2 =
                createAccount(
                        customerId,
                        "088100000062"
                );

        Account account3 =
                createAccount(
                        customerId,
                        "088100000063"
                );

        mockMvc.perform(
                        put(
                                "/account-preferences/display-order"
                        )
                                .with(authentication(
                                        authenticationOf(
                                                customerId
                                        )
                                ))
                                .with(csrf())
                                .header(
                                        "Idempotency-Key",
                                        idempotencyKey()
                                )
                                .contentType(
                                        MediaType.APPLICATION_JSON
                                )
                                .content(
                                        """
                                                {
                                                  "accountIds": [%d, %d, %d]
                                                }
                                                """.formatted(
                                                account3.getAccountId(),
                                                account1.getAccountId(),
                                                account2.getAccountId()
                                        )
                                )
                )
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath("$.code")
                                .value("0000")
                )
                .andExpect(
                        jsonPath("$.message")
                                .value(
                                        "계좌 표시순서가 저장되었습니다."
                                )
                )
                .andExpect(
                        jsonPath("$.data.accountIds[0]")
                                .value(
                                        account3.getAccountId()
                                )
                )
                .andExpect(
                        jsonPath("$.data.accountIds[1]")
                                .value(
                                        account1.getAccountId()
                                )
                )
                .andExpect(
                        jsonPath("$.data.accountIds[2]")
                                .value(
                                        account2.getAccountId()
                                )
                );

        Account saved1 =
                findAccount(
                        account1.getAccountId(),
                        customerId
                );

        Account saved2 =
                findAccount(
                        account2.getAccountId(),
                        customerId
                );

        Account saved3 =
                findAccount(
                        account3.getAccountId(),
                        customerId
                );

        assertThat(saved3.getDisplayOrder())
                .isEqualTo(1);

        assertThat(saved1.getDisplayOrder())
                .isEqualTo(2);

        assertThat(saved2.getDisplayOrder())
                .isEqualTo(3);
    }

    @Test
    @DisplayName("계좌 표시순서를 초기화한다")
    void resetDisplayOrder() throws Exception {
        Long customerId =
                customerTestFixture.createCustomer();

        Account account1 =
                createAccount(
                        customerId,
                        "088100000064"
                );

        Account account2 =
                createAccount(
                        customerId,
                        "088100000065"
                );

        account1.changeDisplayOrder(2);
        account2.changeDisplayOrder(1);

        accountPersistencePort.save(account1);
        accountPersistencePort.save(account2);

        mockMvc.perform(
                        delete(
                                "/account-preferences/display-order"
                        )
                                .with(authentication(
                                        authenticationOf(
                                                customerId
                                        )
                                ))
                                .with(csrf())
                                .header(
                                        "Idempotency-Key",
                                        idempotencyKey()
                                )
                )
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath("$.code")
                                .value("0000")
                )
                .andExpect(
                        jsonPath("$.data.accountIds")
                                .isArray()
                );

        assertThat(
                findAccount(
                        account1.getAccountId(),
                        customerId
                ).getDisplayOrder()
        ).isNull();

        assertThat(
                findAccount(
                        account2.getAccountId(),
                        customerId
                ).getDisplayOrder()
        ).isNull();
    }

    @Test
    @DisplayName("accountIds가 누락되면 CMN0002를 반환한다")
    void rejectMissingAccountIds() throws Exception {
        Long customerId =
                customerTestFixture.createCustomer();

        mockMvc.perform(
                        put(
                                "/account-preferences/display-order"
                        )
                                .with(authentication(
                                        authenticationOf(
                                                customerId
                                        )
                                ))
                                .with(csrf())
                                .header(
                                        "Idempotency-Key",
                                        idempotencyKey()
                                )
                                .contentType(
                                        MediaType.APPLICATION_JSON
                                )
                                .content("{}")
                )
                .andExpect(status().isBadRequest())
                .andExpect(
                        jsonPath("$.code")
                                .value("CMN0002")
                );
    }

    @Test
    @DisplayName("0 이하의 계좌 ID가 포함되면 CMN0001을 반환한다")
    void rejectInvalidAccountId() throws Exception {
        Long customerId =
                customerTestFixture.createCustomer();

        mockMvc.perform(
                        put(
                                "/account-preferences/display-order"
                        )
                                .with(authentication(
                                        authenticationOf(
                                                customerId
                                        )
                                ))
                                .with(csrf())
                                .header(
                                        "Idempotency-Key",
                                        idempotencyKey()
                                )
                                .contentType(
                                        MediaType.APPLICATION_JSON
                                )
                                .content(
                                        """
                                                {
                                                  "accountIds": [1, 0]
                                                }
                                                """
                                )
                )
                .andExpect(status().isBadRequest())
                .andExpect(
                        jsonPath("$.code")
                                .value("CMN0001")
                );
    }

    @Test
    @DisplayName("계좌 ID가 중복되면 ACC0002를 반환한다")
    void rejectDuplicateAccountIds() throws Exception {
        Long customerId =
                customerTestFixture.createCustomer();

        Account account1 =
                createAccount(
                        customerId,
                        "088100000066"
                );

        createAccount(
                customerId,
                "088100000067"
        );

        mockMvc.perform(
                        put(
                                "/account-preferences/display-order"
                        )
                                .with(authentication(
                                        authenticationOf(
                                                customerId
                                        )
                                ))
                                .with(csrf())
                                .header(
                                        "Idempotency-Key",
                                        idempotencyKey()
                                )
                                .contentType(
                                        MediaType.APPLICATION_JSON
                                )
                                .content(
                                        """
                                                {
                                                  "accountIds": [%d, %d]
                                                }
                                                """.formatted(
                                                account1.getAccountId(),
                                                account1.getAccountId()
                                        )
                                )
                )
                .andExpect(status().isBadRequest())
                .andExpect(
                        jsonPath("$.code")
                                .value("ACC0002")
                );
    }

    @Test
    @DisplayName("본인 소유 계좌 일부가 누락되면 ACC0002를 반환한다")
    void rejectMissingOwnedAccount() throws Exception {
        Long customerId =
                customerTestFixture.createCustomer();

        Account account1 =
                createAccount(
                        customerId,
                        "088100000068"
                );

        createAccount(
                customerId,
                "088100000069"
        );

        mockMvc.perform(
                        put(
                                "/account-preferences/display-order"
                        )
                                .with(authentication(
                                        authenticationOf(
                                                customerId
                                        )
                                ))
                                .with(csrf())
                                .header(
                                        "Idempotency-Key",
                                        idempotencyKey()
                                )
                                .contentType(
                                        MediaType.APPLICATION_JSON
                                )
                                .content(
                                        """
                                                {
                                                  "accountIds": [%d]
                                                }
                                                """.formatted(
                                                account1.getAccountId()
                                        )
                                )
                )
                .andExpect(status().isBadRequest())
                .andExpect(
                        jsonPath("$.code")
                                .value("ACC0002")
                );
    }

    @Test
    @DisplayName("타인 소유 계좌가 포함되면 ACC0201을 반환한다")
    void rejectOtherCustomersAccount()
            throws Exception {

        Long customerId =
                customerTestFixture.createCustomer();

        Long otherCustomerId =
                customerTestFixture.createCustomer();

        Account myAccount =
                createAccount(
                        customerId,
                        "088100000070"
                );

        Account otherAccount =
                createAccount(
                        otherCustomerId,
                        "088100000071"
                );

        mockMvc.perform(
                        put(
                                "/account-preferences/display-order"
                        )
                                .with(authentication(
                                        authenticationOf(
                                                customerId
                                        )
                                ))
                                .with(csrf())
                                .header(
                                        "Idempotency-Key",
                                        idempotencyKey()
                                )
                                .contentType(
                                        MediaType.APPLICATION_JSON
                                )
                                .content(
                                        """
                                                {
                                                  "accountIds": [%d, %d]
                                                }
                                                """.formatted(
                                                myAccount.getAccountId(),
                                                otherAccount.getAccountId()
                                        )
                                )
                )
                .andExpect(status().isNotFound())
                .andExpect(
                        jsonPath("$.code")
                                .value("ACC0201")
                );
    }

    @Test
    @DisplayName("Idempotency-Key가 없으면 CMN0002를 반환한다")
    void rejectMissingIdempotencyKey()
            throws Exception {

        Long customerId =
                customerTestFixture.createCustomer();

        Account account =
                createAccount(
                        customerId,
                        "088100000072"
                );

        mockMvc.perform(
                        put(
                                "/account-preferences/display-order"
                        )
                                .with(authentication(
                                        authenticationOf(
                                                customerId
                                        )
                                ))
                                .with(csrf())
                                .contentType(
                                        MediaType.APPLICATION_JSON
                                )
                                .content(
                                        """
                                                {
                                                  "accountIds": [%d]
                                                }
                                                """.formatted(
                                                account.getAccountId()
                                        )
                                )
                )
                .andExpect(status().isBadRequest())
                .andExpect(
                        jsonPath("$.code")
                                .value("CMN0002")
                );
    }

    @Test
    @DisplayName("잘못된 Idempotency-Key는 CMN0001을 반환한다")
    void rejectInvalidIdempotencyKey()
            throws Exception {

        Long customerId =
                customerTestFixture.createCustomer();

        Account account =
                createAccount(
                        customerId,
                        "088100000073"
                );

        mockMvc.perform(
                        put(
                                "/account-preferences/display-order"
                        )
                                .with(authentication(
                                        authenticationOf(
                                                customerId
                                        )
                                ))
                                .with(csrf())
                                .header(
                                        "Idempotency-Key",
                                        "not-a-uuid"
                                )
                                .contentType(
                                        MediaType.APPLICATION_JSON
                                )
                                .content(
                                        """
                                                {
                                                  "accountIds": [%d]
                                                }
                                                """.formatted(
                                                account.getAccountId()
                                        )
                                )
                )
                .andExpect(status().isBadRequest())
                .andExpect(
                        jsonPath("$.code")
                                .value("CMN0001")
                );
    }

    @Test
    @DisplayName("인증 없이 표시순서 저장을 요청하면 401을 반환한다")
    void rejectUnauthenticatedRequest()
            throws Exception {

        mockMvc.perform(
                        put(
                                "/account-preferences/display-order"
                        )
                                .with(csrf())
                                .header(
                                        "Idempotency-Key",
                                        idempotencyKey()
                                )
                                .contentType(
                                        MediaType.APPLICATION_JSON
                                )
                                .content(
                                        """
                                                {
                                                  "accountIds": [1]
                                                }
                                                """
                                )
                )
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("CSRF 토큰 없이 표시순서 저장을 요청하면 403을 반환한다")
    void rejectWithoutCsrf()
            throws Exception {

        Long customerId =
                customerTestFixture.createCustomer();

        Account account =
                createAccount(
                        customerId,
                        "088100000074"
                );

        mockMvc.perform(
                        put(
                                "/account-preferences/display-order"
                        )
                                .with(authentication(
                                        authenticationOf(
                                                customerId
                                        )
                                ))
                                .header(
                                        "Idempotency-Key",
                                        idempotencyKey()
                                )
                                .contentType(
                                        MediaType.APPLICATION_JSON
                                )
                                .content(
                                        """
                                                {
                                                  "accountIds": [%d]
                                                }
                                                """.formatted(
                                                account.getAccountId()
                                        )
                                )
                )
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("같은 Idempotency-Key로 동일한 표시순서 저장 요청을 재전송하면 동일한 응답을 재생한다")
    void replaySameDisplayOrderRequest()
            throws Exception {

        // given
        Long customerId =
                customerTestFixture.createCustomer();

        Account account1 =
                createAccount(
                        customerId,
                        "088100000075"
                );

        Account account2 =
                createAccount(
                        customerId,
                        "088100000076"
                );

        String idempotencyKey =
                idempotencyKey();

        String requestJson =
                """
                        {
                          "accountIds": [%d, %d]
                        }
                        """.formatted(
                        account2.getAccountId(),
                        account1.getAccountId()
                );

        // when - 첫 번째 요청
        MvcResult firstResult =
                mockMvc.perform(
                                put(
                                        "/account-preferences/display-order"
                                )
                                        .with(authentication(
                                                authenticationOf(
                                                        customerId
                                                )
                                        ))
                                        .with(csrf())
                                        .header(
                                                "Idempotency-Key",
                                                idempotencyKey
                                        )
                                        .contentType(
                                                MediaType.APPLICATION_JSON
                                        )
                                        .content(requestJson)
                        )
                        .andExpect(status().isOk())
                        .andExpect(
                                jsonPath("$.code")
                                        .value("0000")
                        )
                        .andReturn();

        /*
         * IdempotencyService.complete()는 JPQL bulk update를 사용하므로
         * 테스트의 동일 persistence context에 PROCESSING 상태가 남을 수 있다.
         *
         * 실제 HTTP 요청에서는 요청별 persistence context가 분리되므로
         * 테스트에서만 명시적으로 비운다.
         */
        entityManager.flush();
        entityManager.clear();

        // when - 같은 키 + 같은 요청 재전송
        MvcResult secondResult =
                mockMvc.perform(
                                put(
                                        "/account-preferences/display-order"
                                )
                                        .with(authentication(
                                                authenticationOf(
                                                        customerId
                                                )
                                        ))
                                        .with(csrf())
                                        .header(
                                                "Idempotency-Key",
                                                idempotencyKey
                                        )
                                        .contentType(
                                                MediaType.APPLICATION_JSON
                                        )
                                        .content(requestJson)
                        )
                        .andExpect(status().isOk())
                        .andExpect(
                                jsonPath("$.code")
                                        .value("0000")
                        )
                        .andExpect(
                                jsonPath(
                                        "$.data.accountIds[0]"
                                ).value(
                                        account2.getAccountId()
                                )
                        )
                        .andExpect(
                                jsonPath(
                                        "$.data.accountIds[1]"
                                ).value(
                                        account1.getAccountId()
                                )
                        )
                        .andReturn();

        // then
        assertThat(
                secondResult
                        .getResponse()
                        .getContentAsString()
        ).isEqualTo(
                firstResult
                        .getResponse()
                        .getContentAsString()
        );

        assertThat(
                findAccount(
                        account2.getAccountId(),
                        customerId
                ).getDisplayOrder()
        ).isEqualTo(1);

        assertThat(
                findAccount(
                        account1.getAccountId(),
                        customerId
                ).getDisplayOrder()
        ).isEqualTo(2);
    }

    @Test
    @DisplayName("같은 Idempotency-Key로 다른 표시순서를 요청하면 CMN0302를 반환한다")
    void rejectSameKeyWithDifferentDisplayOrder()
            throws Exception {

        // given
        Long customerId =
                customerTestFixture.createCustomer();

        Account account1 =
                createAccount(
                        customerId,
                        "088100000077"
                );

        Account account2 =
                createAccount(
                        customerId,
                        "088100000078"
                );

        String idempotencyKey =
                idempotencyKey();

        // 첫 번째 요청
        mockMvc.perform(
                        put(
                                "/account-preferences/display-order"
                        )
                                .with(authentication(
                                        authenticationOf(
                                                customerId
                                        )
                                ))
                                .with(csrf())
                                .header(
                                        "Idempotency-Key",
                                        idempotencyKey
                                )
                                .contentType(
                                        MediaType.APPLICATION_JSON
                                )
                                .content(
                                        """
                                                {
                                                  "accountIds": [%d, %d]
                                                }
                                                """.formatted(
                                                account1.getAccountId(),
                                                account2.getAccountId()
                                        )
                                )
                )
                .andExpect(status().isOk());

        entityManager.flush();
        entityManager.clear();

        // 같은 key지만 표시순서는 반대
        mockMvc.perform(
                        put(
                                "/account-preferences/display-order"
                        )
                                .with(authentication(
                                        authenticationOf(
                                                customerId
                                        )
                                ))
                                .with(csrf())
                                .header(
                                        "Idempotency-Key",
                                        idempotencyKey
                                )
                                .contentType(
                                        MediaType.APPLICATION_JSON
                                )
                                .content(
                                        """
                                                {
                                                  "accountIds": [%d, %d]
                                                }
                                                """.formatted(
                                                account2.getAccountId(),
                                                account1.getAccountId()
                                        )
                                )
                )
                .andExpect(status().isConflict())
                .andExpect(
                        jsonPath("$.code")
                                .value("CMN0302")
                );

        // 첫 요청의 순서가 그대로 유지
        assertThat(
                findAccount(
                        account1.getAccountId(),
                        customerId
                ).getDisplayOrder()
        ).isEqualTo(1);

        assertThat(
                findAccount(
                        account2.getAccountId(),
                        customerId
                ).getDisplayOrder()
        ).isEqualTo(2);
    }

    @Test
    @DisplayName("보유 계좌가 있는데 빈 accountIds를 요청하면 ACC0002를 반환한다")
    void rejectEmptyAccountIdsWhenCustomerHasAccounts()
            throws Exception {

        // given
        Long customerId =
                customerTestFixture.createCustomer();

        createAccount(
                customerId,
                "088100000079"
        );

        // when & then
        mockMvc.perform(
                        put(
                                "/account-preferences/display-order"
                        )
                                .with(authentication(
                                        authenticationOf(
                                                customerId
                                        )
                                ))
                                .with(csrf())
                                .header(
                                        "Idempotency-Key",
                                        idempotencyKey()
                                )
                                .contentType(
                                        MediaType.APPLICATION_JSON
                                )
                                .content(
                                        """
                                                {
                                                  "accountIds": []
                                                }
                                                """
                                )
                )
                .andExpect(status().isBadRequest())
                .andExpect(
                        jsonPath("$.code")
                                .value("ACC0002")
                );
    }

    private Account createAccount(
            Long customerId,
            String accountNumber
    ) {
        Account account =
                Account.open(
                        accountNumber,
                        customerId,
                        null,
                        AccountType.DEMAND_DEPOSIT,
                        PASSWORD_HASH,
                        LocalDateTime.of(
                                2026,
                                8,
                                1,
                                10,
                                0
                        ),
                        null
                );

        return accountPersistencePort.save(account);
    }

    private Account findAccount(
            Long accountId,
            Long customerId
    ) {
        return accountPersistencePort
                .findByAccountIdAndCustomerId(
                        accountId,
                        customerId
                )
                .orElseThrow();
    }

    private UsernamePasswordAuthenticationToken
    authenticationOf(
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

    private String idempotencyKey() {
        return UUID.randomUUID().toString();
    }
}