package com.shinhan.corebank.account.adapter.in.web;

import com.shinhan.corebank.IntegrationTestSupport;
import com.shinhan.corebank.account.application.port.in.AccountTransactionQueryUseCase;
import com.shinhan.corebank.account.application.port.out.AccountPersistencePort;
import com.shinhan.corebank.account.domain.Account;
import com.shinhan.corebank.account.domain.AccountStatus;
import com.shinhan.corebank.account.domain.AccountType;
import com.shinhan.corebank.account.domain.exception.AccountErrorCode;
import com.shinhan.corebank.account.support.CustomerTestFixture;
import com.shinhan.corebank.auth.api.AuthenticatedCustomer;
import com.shinhan.corebank.common.exception.BusinessException;
import com.shinhan.corebank.transfer.application.port.in.LedgerHistoryItem;
import com.shinhan.corebank.transfer.application.port.in.LedgerHistoryResult;
import com.shinhan.corebank.transfer.application.port.in.LedgerHistorySummary;
import com.shinhan.corebank.transfer.domain.LedgerDirection;
import com.shinhan.corebank.transfer.domain.TransferChannel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
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

    @MockitoBean
    private AccountTransactionQueryUseCase accountTransactionQueryUseCase;

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
                        "$.data.items[0].accounts[0].withdrawalRegistered"
                ).value(true))

                .andExpect(jsonPath(
                        "$.data.items[0].accounts[0].transferEnabled"
                ).value(true));
    }

    @Test
    @DisplayName("거래정지된 출금계좌는 등록 상태를 유지하지만 이체할 수 없다")
    void getAccountsDistinguishesWithdrawalRegistrationFromTransferAvailability()
            throws Exception {

        // given
        Long customerId =
                customerTestFixture.createCustomer();

        Account suspendedAccount =
                createDemandDepositAccount(
                        customerId,
                        "088100000013",
                        1_000_000L,
                        null,
                        AccountStatus.SUSPENDED,
                        true
                );

        accountPersistencePort.save(
                suspendedAccount
        );

        // when & then
        mockMvc.perform(
                        get("/accounts")
                                .with(
                                        authentication(
                                                authenticationOf(customerId)
                                        )
                                )
                )
                .andExpect(status().isOk())

                .andExpect(jsonPath(
                        "$.data.items[0].accounts[0].status"
                ).value("SUSPENDED"))

                .andExpect(jsonPath(
                        "$.data.items[0].accounts[0].withdrawalRegistered"
                ).value(true))

                .andExpect(jsonPath(
                        "$.data.items[0].accounts[0].transferEnabled"
                ).value(false));
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

    @Test
    @DisplayName("본인 계좌에 별명을 등록한다")
    void changeAlias() throws Exception {
        // given
        Long customerId =
                customerTestFixture
                        .createCustomer();

        Account account =
                createAccount(
                        customerId,
                        "088100000031",
                        null
                );

        // when & then
        mockMvc.perform(
                        put(
                                "/accounts/{accountId}/alias",
                                account.getAccountId()
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
                                                  "alias": "생활비통장"
                                                }
                                                """
                                )
                )
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath("$.code")
                                .value("0000")
                )
                .andExpect(
                        jsonPath(
                                "$.data.accountId"
                        ).value(
                                account.getAccountId()
                        )
                )
                .andExpect(
                        jsonPath("$.data.alias")
                                .value("생활비통장")
                );

        Account savedAccount =
                accountPersistencePort
                        .findByAccountIdAndCustomerId(
                                account.getAccountId(),
                                customerId
                        )
                        .orElseThrow();

        assertThat(savedAccount.getAlias())
                .isEqualTo("생활비통장");
    }

    @Test
    @DisplayName("기존 계좌별명을 수정한다")
    void updateAlias() throws Exception {
        // given
        Long customerId =
                customerTestFixture
                        .createCustomer();

        Account account =
                createAccount(
                        customerId,
                        "088100000032",
                        "생활비통장"
                );

        // when & then
        mockMvc.perform(
                        put(
                                "/accounts/{accountId}/alias",
                                account.getAccountId()
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
                                                  "alias": "급여통장"
                                                }
                                                """
                                )
                )
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath("$.data.alias")
                                .value("급여통장")
                );

        Account savedAccount =
                accountPersistencePort
                        .findByAccountIdAndCustomerId(
                                account.getAccountId(),
                                customerId
                        )
                        .orElseThrow();

        assertThat(savedAccount.getAlias())
                .isEqualTo("급여통장");
    }

    @Test
    @DisplayName("계좌별명을 삭제한다")
    void deleteAlias() throws Exception {
        // given
        Long customerId =
                customerTestFixture
                        .createCustomer();

        Account account =
                createAccount(
                        customerId,
                        "088100000033",
                        "생활비통장"
                );

        // when & then
        mockMvc.perform(
                        delete(
                                "/accounts/{accountId}/alias",
                                account.getAccountId()
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
                );

        Account savedAccount =
                accountPersistencePort
                        .findByAccountIdAndCustomerId(
                                account.getAccountId(),
                                customerId
                        )
                        .orElseThrow();

        assertThat(savedAccount.getAlias())
                .isNull();
    }

    @Test
    @DisplayName("타인 계좌별명 변경은 ACC0201을 반환한다")
    void rejectChangingOtherCustomersAlias()
            throws Exception {

        // given
        Long ownerCustomerId =
                customerTestFixture
                        .createCustomer();

        Long requesterCustomerId =
                customerTestFixture
                        .createCustomer();

        Account account =
                createAccount(
                        ownerCustomerId,
                        "088100000034",
                        null
                );

        // when & then
        mockMvc.perform(
                        put(
                                "/accounts/{accountId}/alias",
                                account.getAccountId()
                        )
                                .with(authentication(
                                        authenticationOf(
                                                requesterCustomerId
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
                                                  "alias": "내계좌"
                                                }
                                                """
                                )
                )
                .andExpect(status().isNotFound())
                .andExpect(
                        jsonPath("$.code")
                                .value("ACC0201")
                );
    }

    @Test
    @DisplayName("한글 12자를 초과하는 별명은 ACC0001을 반환한다")
    void rejectTooLongKoreanAlias()
            throws Exception {

        // given
        Long customerId =
                customerTestFixture
                        .createCustomer();

        Account account =
                createAccount(
                        customerId,
                        "088100000035",
                        null
                );

        // when & then
        mockMvc.perform(
                        put(
                                "/accounts/{accountId}/alias",
                                account.getAccountId()
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
                                                  "alias": "가나다라마바사아자차카타파"
                                                }
                                                """
                                )
                )
                .andExpect(status().isBadRequest())
                .andExpect(
                        jsonPath("$.code")
                                .value("ACC0001")
                );
    }

    @Test
    @DisplayName("Idempotency-Key가 없으면 CMN0002를 반환한다")
    void rejectMissingIdempotencyKey()
            throws Exception {

        // given
        Long customerId =
                customerTestFixture
                        .createCustomer();

        Account account =
                createAccount(
                        customerId,
                        "088100000036",
                        null
                );

        // when & then
        mockMvc.perform(
                        put(
                                "/accounts/{accountId}/alias",
                                account.getAccountId()
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
                                                  "alias": "생활비"
                                                }
                                                """
                                )
                )
                .andExpect(status().isBadRequest())
                .andExpect(
                        jsonPath("$.code")
                                .value("CMN0002")
                );
    }

    @Test
    @DisplayName("UUID v4 형식이 아닌 멱등키는 CMN0001을 반환한다")
    void rejectInvalidIdempotencyKey()
            throws Exception {

        // given
        Long customerId =
                customerTestFixture
                        .createCustomer();

        Account account =
                createAccount(
                        customerId,
                        "088100000037",
                        null
                );

        // when & then
        mockMvc.perform(
                        put(
                                "/accounts/{accountId}/alias",
                                account.getAccountId()
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
                                                  "alias": "생활비"
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
    @DisplayName("인증 없이 계좌별명 변경을 요청하면 401을 반환한다")
    void rejectUnauthenticatedAliasChange()
            throws Exception {

        // when & then
        mockMvc.perform(
                        put(
                                "/accounts/{accountId}/alias",
                                1L
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
                                                  "alias": "생활비"
                                                }
                                                """
                                )
                )
                .andExpect(
                        status().isUnauthorized()
                );
    }

    @Test
    @DisplayName("CSRF 토큰 없이 계좌별명 변경을 요청하면 403을 반환한다")
    void rejectAliasChangeWithoutCsrf()
            throws Exception {

        // given
        Long customerId =
                customerTestFixture.createCustomer();

        Account account =
                createAccount(
                        customerId,
                        "088100000038",
                        null
                );

        // when & then
        mockMvc.perform(
                        put(
                                "/accounts/{accountId}/alias",
                                account.getAccountId()
                        )
                                .with(authentication(
                                        authenticationOf(customerId)
                                ))
                                // intentionally no csrf()
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
                                                  "alias": "생활비"
                                                }
                                                """
                                )
                )
                .andExpect(status().isForbidden())
                .andExpect(
                        jsonPath("$.code")
                                .value("CMN0102")
                );
    }

    @Test
    @DisplayName("계좌별명이 누락되면 CMN0002를 반환한다")
    void rejectMissingAlias()
            throws Exception {

        Long customerId =
                customerTestFixture.createCustomer();

        Account account =
                createAccount(
                        customerId,
                        "088100000038",
                        null
                );

        mockMvc.perform(
                        put(
                                "/accounts/{accountId}/alias",
                                account.getAccountId()
                        )
                                .with(authentication(
                                        authenticationOf(customerId)
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
    @DisplayName("계좌별명이 공백뿐이면 CMN0002를 반환한다")
    void rejectBlankAlias()
            throws Exception {

        Long customerId =
                customerTestFixture.createCustomer();

        Account account =
                createAccount(
                        customerId,
                        "088100000039",
                        null
                );

        mockMvc.perform(
                        put(
                                "/accounts/{accountId}/alias",
                                account.getAccountId()
                        )
                                .with(authentication(
                                        authenticationOf(customerId)
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
                                                  "alias": "   "
                                                }
                                                """
                                )
                )
                .andExpect(status().isBadRequest())
                .andExpect(
                        jsonPath("$.code")
                                .value("CMN0002")
                );
    }

    @Test
    @DisplayName("계좌 거래내역을 조회한다")
    void getAccountTransactions() throws Exception {
        // given
        Long customerId = 1L;
        Long accountId = 101L;

        LedgerHistoryResult result =
                LedgerHistoryResult.builder()
                        .summary(
                                LedgerHistorySummary.builder()
                                        .depositCount(3L)
                                        .depositAmount(150_000L)
                                        .withdrawalCount(5L)
                                        .withdrawalAmount(82_000L)
                                        .build()
                        )
                        .page(0)
                        .size(10)
                        .totalCount(8L)
                        .totalPages(1)
                        .items(
                                List.of(
                                        LedgerHistoryItem.builder()
                                                .ledgerEntryId(1001L)
                                                .transactionNumber(
                                                        "20260820WB0000000001"
                                                )
                                                .occurredAt(
                                                        LocalDateTime.of(
                                                                2026,
                                                                8,
                                                                20,
                                                                13,
                                                                20
                                                        )
                                                )
                                                .transactionType(
                                                        "IMMEDIATE_TRANSFER"
                                                )
                                                .direction(
                                                        LedgerDirection.WITHDRAWAL
                                                )
                                                .amount(20_000L)
                                                .transactionContent("점심")
                                                .balanceAfter(980_000L)
                                                .channel(TransferChannel.WB)
                                                .reversed(true)
                                                .reversalId(2001L)
                                                .build()
                                )
                        )
                        .build();

        given(
                accountTransactionQueryUseCase
                        .getTransactions(
                                eq(customerId),
                                eq(accountId),
                                any(),
                                any(),
                                any(),
                                any(),
                                any(),
                                eq(1),
                                eq(10)
                        )
        ).willReturn(result);

        // when & then
        mockMvc.perform(
                        get(
                                "/accounts/{accountId}/transactions",
                                accountId
                        )
                                .with(
                                        authentication(
                                                authenticationOf(customerId)
                                        )
                                )
                                .param("fromDate", "2026-08-01")
                                .param("toDate", "2026-08-20")
                                .param("direction", "ALL")
                                .param("keyword", "점심")
                                .param("sort", "LATEST")
                                .param("page", "1")
                                .param("size", "10")
                )
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath("$.code")
                                .value("0000")
                )
                .andExpect(
                        jsonPath("$.message")
                                .value("정상 처리되었습니다.")
                )
                .andExpect(
                        jsonPath("$.data.summary.depositCount")
                                .value(3)
                )
                .andExpect(
                        jsonPath("$.data.summary.depositAmount")
                                .value(150_000)
                )
                .andExpect(
                        jsonPath("$.data.summary.withdrawalCount")
                                .value(5)
                )
                .andExpect(
                        jsonPath("$.data.summary.withdrawalAmount")
                                .value(82_000)
                )
                .andExpect(
                        jsonPath("$.data.page")
                                .value(1)
                )
                .andExpect(
                        jsonPath("$.data.size")
                                .value(10)
                )
                .andExpect(
                        jsonPath("$.data.totalCount")
                                .value(8)
                )
                .andExpect(
                        jsonPath("$.data.totalPages")
                                .value(1)
                )
                .andExpect(
                        jsonPath("$.data.items.length()")
                                .value(1)
                )
                .andExpect(
                        jsonPath("$.data.items[0].ledgerEntryId")
                                .value(1001L)
                )
                .andExpect(
                        jsonPath("$.data.items[0].transactionNumber")
                                .value("20260820WB0000000001")
                )
                .andExpect(
                        jsonPath("$.data.items[0].occurredAt")
                                .value("2026-08-20T13:20:00")
                )
                .andExpect(
                        jsonPath("$.data.items[0].transactionType")
                                .value("IMMEDIATE_TRANSFER")
                )
                .andExpect(
                        jsonPath("$.data.items[0].withdrawalAmount")
                                .value(20_000L)
                )
                .andExpect(
                        jsonPath("$.data.items[0].depositAmount")
                                .doesNotExist()
                )
                .andExpect(
                        jsonPath("$.data.items[0].transactionContent")
                                .value("점심")
                )
                .andExpect(
                        jsonPath("$.data.items[0].balanceAfter")
                                .value(980_000L)
                )
                .andExpect(
                        jsonPath("$.data.items[0].channel")
                                .value("WB")
                ).andExpect(
                        jsonPath("$.data.items[0].reversed")
                                .value(true)
                )
                .andExpect(
                        jsonPath("$.data.items[0].reversalId")
                                .value(2001L)
                );
    }

    @Test
    @DisplayName("거래내역이 없으면 200과 빈 목록을 반환한다")
    void getAccountTransactionsReturnsEmptyItems()
            throws Exception {

        // given
        Long customerId = 1L;
        Long accountId = 101L;

        LedgerHistoryResult result =
                LedgerHistoryResult.builder()
                        .summary(
                                LedgerHistorySummary.empty()
                        )
                        .page(0)
                        .size(10)
                        .totalCount(0L)
                        .totalPages(0)
                        .items(List.of())
                        .build();

        given(
                accountTransactionQueryUseCase
                        .getTransactions(
                                eq(customerId),
                                eq(accountId),
                                isNull(),
                                isNull(),
                                isNull(),
                                isNull(),
                                isNull(),
                                eq(1),
                                eq(10)
                        )
        ).willReturn(result);

        // when & then
        mockMvc.perform(
                        get(
                                "/accounts/{accountId}/transactions",
                                accountId
                        )
                                .with(
                                        authentication(
                                                authenticationOf(customerId)
                                        )
                                )
                )
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath("$.code")
                                .value("0000")
                )
                .andExpect(
                        jsonPath("$.data.page")
                                .value(1)
                )
                .andExpect(
                        jsonPath("$.data.size")
                                .value(10)
                )
                .andExpect(
                        jsonPath("$.data.totalCount")
                                .value(0)
                )
                .andExpect(
                        jsonPath("$.data.totalPages")
                                .value(0)
                )
                .andExpect(
                        jsonPath("$.data.summary.depositCount")
                                .value(0)
                )
                .andExpect(
                        jsonPath("$.data.summary.withdrawalCount")
                                .value(0)
                )
                .andExpect(
                        jsonPath("$.data.items")
                                .isArray()
                )
                .andExpect(
                        jsonPath("$.data.items")
                                .isEmpty()
                );
    }

    @Test
    @DisplayName("유효하지 않은 거래 방향은 CMN0001을 반환한다")
    void rejectInvalidTransactionDirection()
            throws Exception {

        Long customerId = 1L;

        mockMvc.perform(
                        get(
                                "/accounts/{accountId}/transactions",
                                101L
                        )
                                .with(
                                        authentication(
                                                authenticationOf(customerId)
                                        )
                                )
                                .param(
                                        "direction",
                                        "INVALID"
                                )
                )
                .andExpect(status().isBadRequest())
                .andExpect(
                        jsonPath("$.code")
                                .value("CMN0001")
                );

        verify(
                accountTransactionQueryUseCase,
                never()
        ).getTransactions(
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                anyInt(),
                anyInt()
        );
    }

    @Test
    @DisplayName("유효하지 않은 정렬 기준은 CMN0001을 반환한다")
    void rejectInvalidTransactionSort()
            throws Exception {

        Long customerId = 1L;

        mockMvc.perform(
                        get(
                                "/accounts/{accountId}/transactions",
                                101L
                        )
                                .with(
                                        authentication(
                                                authenticationOf(customerId)
                                        )
                                )
                                .param(
                                        "sort",
                                        "INVALID"
                                )
                )
                .andExpect(status().isBadRequest())
                .andExpect(
                        jsonPath("$.code")
                                .value("CMN0001")
                );

        verify(
                accountTransactionQueryUseCase,
                never()
        ).getTransactions(
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                anyInt(),
                anyInt()
        );
    }

    @Test
    @DisplayName("페이지 번호가 0이면 CMN0001을 반환한다")
    void rejectZeroTransactionPage()
            throws Exception {

        Long customerId = 1L;

        mockMvc.perform(
                        get(
                                "/accounts/{accountId}/transactions",
                                101L
                        )
                                .with(
                                        authentication(
                                                authenticationOf(customerId)
                                        )
                                )
                                .param(
                                        "page",
                                        "0"
                                )
                )
                .andExpect(status().isBadRequest())
                .andExpect(
                        jsonPath("$.code")
                                .value("CMN0001")
                );

        verify(
                accountTransactionQueryUseCase,
                never()
        ).getTransactions(
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                anyInt(),
                anyInt()
        );
    }

    @Test
    @DisplayName("잘못된 날짜 형식은 CMN0001을 반환한다")
    void rejectInvalidTransactionDateFormat()
            throws Exception {

        Long customerId = 1L;

        mockMvc.perform(
                        get(
                                "/accounts/{accountId}/transactions",
                                101L
                        )
                                .with(
                                        authentication(
                                                authenticationOf(customerId)
                                        )
                                )
                                .param(
                                        "fromDate",
                                        "2026-99-99"
                                )
                )
                .andExpect(status().isBadRequest())
                .andExpect(
                        jsonPath("$.code")
                                .value("CMN0001")
                );

        verify(
                accountTransactionQueryUseCase,
                never()
        ).getTransactions(
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                anyInt(),
                anyInt()
        );
    }

    @Test
    @DisplayName("본인 소유가 아닌 계좌 거래내역 조회는 ACC0201을 반환한다")
    void rejectOtherCustomersAccountTransactions()
            throws Exception {

        Long customerId = 1L;
        Long accountId = 999L;

        given(
                accountTransactionQueryUseCase
                        .getTransactions(
                                eq(customerId),
                                eq(accountId),
                                isNull(),
                                isNull(),
                                isNull(),
                                isNull(),
                                isNull(),
                                eq(1),
                                eq(10)
                        )
        ).willThrow(
                new BusinessException(
                        AccountErrorCode
                                .ACCOUNT_NOT_FOUND_OR_FORBIDDEN
                )
        );

        mockMvc.perform(
                        get(
                                "/accounts/{accountId}/transactions",
                                accountId
                        )
                                .with(
                                        authentication(
                                                authenticationOf(customerId)
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
    @DisplayName("인증 없이 계좌 거래내역을 조회하면 401을 반환한다")
    void rejectUnauthenticatedAccountTransactionQuery()
            throws Exception {

        mockMvc.perform(
                        get(
                                "/accounts/{accountId}/transactions",
                                101L
                        )
                )
                .andExpect(
                        status().isUnauthorized()
                );

        verify(
                accountTransactionQueryUseCase,
                never()
        ).getTransactions(
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                anyInt(),
                anyInt()
        );
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

    private Account createAccount(
            Long customerId,
            String accountNumber,
            String alias
    ) {
        Account account = Account.open(
                accountNumber,
                customerId,
                null,
                AccountType.DEMAND_DEPOSIT,
                PASSWORD_HASH,
                LocalDateTime.of(
                        2026, 8, 1, 10, 0
                ),
                null
        );

        if (alias != null) {
            account.changeAlias(alias);
        }

        return accountPersistencePort.save(
                account
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

    private String idempotencyKey() {
        return UUID.randomUUID().toString();
    }
}