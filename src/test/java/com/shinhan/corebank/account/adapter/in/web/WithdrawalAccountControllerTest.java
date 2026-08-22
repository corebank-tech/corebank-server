package com.shinhan.corebank.account.adapter.in.web;

import com.shinhan.corebank.IntegrationTestSupport;
import com.shinhan.corebank.account.application.port.out.WithdrawalAccountAuthVerificationPort;
import com.shinhan.corebank.account.application.port.out.AccountPersistencePort;
import com.shinhan.corebank.account.domain.Account;
import com.shinhan.corebank.account.domain.AccountStatus;
import com.shinhan.corebank.account.domain.AccountType;
import com.shinhan.corebank.account.support.CustomerTestFixture;
import com.shinhan.corebank.auth.api.AuthenticatedCustomer;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.endsWith;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
@Transactional
class WithdrawalAccountControllerTest
        extends IntegrationTestSupport {

    private static final String PASSWORD_HASH =
            "$2a$10$34abEWY4uXLwTEnT5hNow.603a5rWofFx7Bnj59agU.PsESK0v/Yq";

    private static final String ACCOUNT_PASSWORD_AUTH_TOKEN =
            "APW-AUTH-TEST";

    private static final String OTP_AUTH_TOKEN =
            "OTP-AUTH-TEST";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AccountPersistencePort accountPersistencePort;

    @Autowired
    private CustomerTestFixture customerTestFixture;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    // Controller 계약 테스트는 실제 일회용 인증 토큰 저장소와 분리한다.
    @MockitoBean
    private WithdrawalAccountAuthVerificationPort authVerificationPort;

    @Test
    @DisplayName("본인 입출금계좌를 출금계좌로 등록한다")
    void registerWithdrawalAccount()
            throws Exception {

        // given
        Long customerId =
                customerTestFixture.createCustomer();

        Account account =
                createAccount(
                        customerId,
                        "088199900001",
                        AccountStatus.ACTIVE
                );

        // when & then
        mockMvc.perform(
                        put(
                                "/withdrawal-accounts/{accountId}",
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
                                        requestJson(
                                                ACCOUNT_PASSWORD_AUTH_TOKEN,
                                                OTP_AUTH_TOKEN
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
                                        "출금계좌가 등록되었습니다."
                                )
                )
                .andExpect(
                        jsonPath("$.data.accountId")
                                .value(
                                        account.getAccountId()
                                )
                )
                .andExpect(
                        jsonPath("$.data.registeredAt")
                                .isNotEmpty()
                )
                .andExpect(
                        jsonPath("$.data.registeredAt")
                                .value(
                                        endsWith("+09:00")
                                )
                );

        /*
         * 실제 DB 상태를 다시 읽기 위해
         * 현재 persistence context를 비운다.
         */
        entityManager.flush();
        entityManager.clear();

        Account savedAccount =
                findAccount(
                        account.getAccountId(),
                        customerId
                );

        assertThat(
                savedAccount.isWithdrawalRegistered()
        ).isTrue();

        assertThat(
                savedAccount.getWithdrawalRegisteredAt()
        ).isNotNull();
    }

    @Test
    @DisplayName("같은 Idempotency-Key로 재요청해도 중복 상태 변경이 발생하지 않는다")
    void replaySameRequest()
            throws Exception {

        // given
        Long customerId =
                customerTestFixture.createCustomer();

        Account account =
                createAccount(
                        customerId,
                        "088199900002",
                        AccountStatus.ACTIVE
                );

        String idempotencyKey =
                idempotencyKey();

        // 첫 번째 요청
        mockMvc.perform(
                        put(
                                "/withdrawal-accounts/{accountId}",
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
                                        idempotencyKey
                                )
                                .contentType(
                                        MediaType.APPLICATION_JSON
                                )
                                .content(
                                        requestJson(
                                                "APW-AUTH-FIRST",
                                                "OTP-AUTH-FIRST"
                                        )
                                )
                )
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath("$.code")
                                .value("0000")
                )
                .andExpect(
                        jsonPath("$.data.accountId")
                                .value(account.getAccountId())
                );

        /*
         * IdempotencyService.complete()가 JPQL bulk update를
         * 사용하므로 동일 테스트 persistence context에는
         * PROCESSING 상태가 남을 수 있다.
         */
        entityManager.flush();
        entityManager.clear();

        Account afterFirstRequest =
                findAccount(
                        account.getAccountId(),
                        customerId
                );

        LocalDateTime firstRegisteredAt =
                afterFirstRequest
                        .getWithdrawalRegisteredAt();

        assertThat(
                afterFirstRequest
                        .isWithdrawalRegistered()
        ).isTrue();

        assertThat(firstRegisteredAt)
                .isNotNull();

        /*
         * 인증 토큰은 일회성 인증 수단이므로
         * fingerprint에 포함하지 않는다.
         *
         * 토큰이 재발급되어도 동일한
         * 출금계좌 등록 요청으로 판단한다.
         */
        mockMvc.perform(
                        put(
                                "/withdrawal-accounts/{accountId}",
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
                                        idempotencyKey
                                )
                                .contentType(
                                        MediaType.APPLICATION_JSON
                                )
                                .content(
                                        requestJson(
                                                "APW-AUTH-SECOND",
                                                "OTP-AUTH-SECOND"
                                        )
                                )
                )
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath("$.code")
                                .value("0000")
                )
                .andExpect(
                        jsonPath("$.data.accountId")
                                .value(account.getAccountId())
                );

        entityManager.flush();
        entityManager.clear();

        // then
        Account afterSecondRequest =
                findAccount(
                        account.getAccountId(),
                        customerId
                );

        assertThat(
                afterSecondRequest
                        .isWithdrawalRegistered()
        ).isTrue();

        assertThat(
                afterSecondRequest
                        .getWithdrawalRegisteredAt()
        ).isEqualTo(
                firstRegisteredAt
        );
    }

    @Test
    @DisplayName(
            "같은 Idempotency-Key를 다른 계좌 등록에 사용하면 CMN0302를 반환한다"
    )
    void rejectSameKeyWithDifferentAccount()
            throws Exception {

        // given
        Long customerId =
                customerTestFixture.createCustomer();

        Account account1 =
                createAccount(
                        customerId,
                        "088199900003",
                        AccountStatus.ACTIVE
                );

        Account account2 =
                createAccount(
                        customerId,
                        "088199900004",
                        AccountStatus.ACTIVE
                );

        String idempotencyKey =
                idempotencyKey();

        // 첫 번째 계좌 등록
        mockMvc.perform(
                        put(
                                "/withdrawal-accounts/{accountId}",
                                account1.getAccountId()
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
                                        requestJson(
                                                ACCOUNT_PASSWORD_AUTH_TOKEN,
                                                OTP_AUTH_TOKEN
                                        )
                                )
                )
                .andExpect(status().isOk());

        entityManager.flush();
        entityManager.clear();

        // 같은 key로 다른 계좌 등록
        mockMvc.perform(
                        put(
                                "/withdrawal-accounts/{accountId}",
                                account2.getAccountId()
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
                                        requestJson(
                                                ACCOUNT_PASSWORD_AUTH_TOKEN,
                                                OTP_AUTH_TOKEN
                                        )
                                )
                )
                .andExpect(status().isConflict())
                .andExpect(
                        jsonPath("$.code")
                                .value("CMN0302")
                );

        Account secondAccount =
                findAccount(
                        account2.getAccountId(),
                        customerId
                );

        assertThat(
                secondAccount.isWithdrawalRegistered()
        ).isFalse();
    }

    @Test
    @DisplayName(
            "Idempotency-Key가 없으면 CMN0002를 반환한다"
    )
    void rejectMissingIdempotencyKey()
            throws Exception {

        // given
        Long customerId =
                customerTestFixture.createCustomer();

        Account account =
                createAccount(
                        customerId,
                        "088199900005",
                        AccountStatus.ACTIVE
                );

        // when & then
        mockMvc.perform(
                        put(
                                "/withdrawal-accounts/{accountId}",
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
                                        requestJson(
                                                ACCOUNT_PASSWORD_AUTH_TOKEN,
                                                OTP_AUTH_TOKEN
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
    @DisplayName(
            "UUID v4 형식이 아닌 Idempotency-Key는 CMN0001을 반환한다"
    )
    void rejectInvalidIdempotencyKey()
            throws Exception {

        // given
        Long customerId =
                customerTestFixture.createCustomer();

        Account account =
                createAccount(
                        customerId,
                        "088199900006",
                        AccountStatus.ACTIVE
                );

        // when & then
        mockMvc.perform(
                        put(
                                "/withdrawal-accounts/{accountId}",
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
                                        requestJson(
                                                ACCOUNT_PASSWORD_AUTH_TOKEN,
                                                OTP_AUTH_TOKEN
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
    @DisplayName(
            "계좌비밀번호 인증토큰이 공백이면 CMN0001을 반환한다"
    )
    void rejectBlankAccountPasswordAuthToken()
            throws Exception {

        // given
        Long customerId =
                customerTestFixture.createCustomer();

        Account account =
                createAccount(
                        customerId,
                        "088199900007",
                        AccountStatus.ACTIVE
                );

        // when & then
        mockMvc.perform(
                        put(
                                "/withdrawal-accounts/{accountId}",
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
                                        requestJson(
                                                "   ",
                                                OTP_AUTH_TOKEN
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
    @DisplayName(
            "OTP 인증토큰이 공백이면 CMN0001을 반환한다"
    )
    void rejectBlankOtpAuthToken()
            throws Exception {

        // given
        Long customerId =
                customerTestFixture.createCustomer();

        Account account =
                createAccount(
                        customerId,
                        "088199900008",
                        AccountStatus.ACTIVE
                );

        // when & then
        mockMvc.perform(
                        put(
                                "/withdrawal-accounts/{accountId}",
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
                                        requestJson(
                                                ACCOUNT_PASSWORD_AUTH_TOKEN,
                                                "   "
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
    @DisplayName(
            "0 이하의 accountId는 CMN0001을 반환한다"
    )
    void rejectInvalidAccountId()
            throws Exception {

        // given
        Long customerId =
                customerTestFixture.createCustomer();

        // when & then
        mockMvc.perform(
                        put(
                                "/withdrawal-accounts/{accountId}",
                                0L
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
                                        requestJson(
                                                ACCOUNT_PASSWORD_AUTH_TOKEN,
                                                OTP_AUTH_TOKEN
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
    @DisplayName(
            "인증 없이 출금계좌 등록을 요청하면 401을 반환한다"
    )
    void rejectUnauthenticatedRequest()
            throws Exception {

        mockMvc.perform(
                        put(
                                "/withdrawal-accounts/{accountId}",
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
                                        requestJson(
                                                ACCOUNT_PASSWORD_AUTH_TOKEN,
                                                OTP_AUTH_TOKEN
                                        )
                                )
                )
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName(
            "CSRF 토큰 없이 출금계좌 등록을 요청하면 403을 반환한다"
    )
    void rejectWithoutCsrf()
            throws Exception {

        // given
        Long customerId =
                customerTestFixture.createCustomer();

        Account account =
                createAccount(
                        customerId,
                        "088199900009",
                        AccountStatus.ACTIVE
                );

        // when & then
        mockMvc.perform(
                        put(
                                "/withdrawal-accounts/{accountId}",
                                account.getAccountId()
                        )
                                .with(authentication(
                                        authenticationOf(
                                                customerId
                                        )
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
                                        requestJson(
                                                ACCOUNT_PASSWORD_AUTH_TOKEN,
                                                OTP_AUTH_TOKEN
                                        )
                                )
                )
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName(
            "타인 소유 계좌 등록은 ACC0201을 반환한다"
    )
    void rejectRegisterOtherCustomersAccount()
            throws Exception {

        // given
        Long ownerCustomerId =
                customerTestFixture.createCustomer();

        Long requesterCustomerId =
                customerTestFixture.createCustomer();

        Account account =
                createAccount(
                        ownerCustomerId,
                        "088199900010",
                        AccountStatus.ACTIVE
                );

        // when & then
        mockMvc.perform(
                        put(
                                "/withdrawal-accounts/{accountId}",
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
                                        requestJson(
                                                ACCOUNT_PASSWORD_AUTH_TOKEN,
                                                OTP_AUTH_TOKEN
                                        )
                                )
                )
                .andExpect(status().isNotFound())
                .andExpect(
                        jsonPath("$.code")
                                .value("ACC0201")
                );

        Account savedAccount =
                findAccount(
                        account.getAccountId(),
                        ownerCustomerId
                );

        assertThat(
                savedAccount.isWithdrawalRegistered()
        ).isFalse();
    }

    @Test
    @DisplayName(
            "거래정지 계좌 등록은 ACC0301을 반환한다"
    )
    void rejectSuspendedAccount()
            throws Exception {

        // given
        Long customerId =
                customerTestFixture.createCustomer();

        Account account =
                createAccount(
                        customerId,
                        "088199900011",
                        AccountStatus.SUSPENDED
                );

        // when & then
        mockMvc.perform(
                        put(
                                "/withdrawal-accounts/{accountId}",
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
                                        requestJson(
                                                ACCOUNT_PASSWORD_AUTH_TOKEN,
                                                OTP_AUTH_TOKEN
                                        )
                                )
                )
                .andExpect(status().isConflict())
                .andExpect(
                        jsonPath("$.code")
                                .value("ACC0301")
                );

        Account savedAccount =
                findAccount(
                        account.getAccountId(),
                        customerId
                );

        assertThat(
                savedAccount.isWithdrawalRegistered()
        ).isFalse();
    }

    private Account createAccount(
            Long customerId,
            String accountNumber,
            AccountStatus status
    ) {
        Account account =
                Account.reconstitute(
                        null,
                        accountNumber,
                        customerId,
                        null,
                        AccountType.DEMAND_DEPOSIT,
                        1_000_000L,
                        status,
                        PASSWORD_HASH,
                        0,
                        false,
                        null,
                        null,
                        false,
                        null,
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
                        null,
                        null,
                        null
                );

        return accountPersistencePort.save(account);
    }

    @Test
    @DisplayName("등록된 출금계좌를 삭제한다")
    void unregisterWithdrawalAccount()
            throws Exception {

        // given
        Long customerId =
                customerTestFixture.createCustomer();

        Account account =
                createRegisteredWithdrawalAccount(
                        customerId,
                        "088199900031"
                );

        // when & then
        mockMvc.perform(
                        delete(
                                "/withdrawal-accounts/{accountId}",
                                account.getAccountId()
                        )
                                .with(
                                        authentication(
                                                authenticationOf(
                                                        customerId
                                                )
                                        )
                                )
                                .with(csrf())
                                .header(
                                        "Idempotency-Key",
                                        idempotencyKey()
                                )
                )
                .andExpect(
                        status().isOk()
                )
                .andExpect(
                        jsonPath("$.code")
                                .value("0000")
                )
                .andExpect(
                        jsonPath("$.message")
                                .value(
                                        "출금계좌 등록이 삭제되었습니다."
                                )
                )
                .andExpect(
                        jsonPath("$.data.accountId")
                                .value(
                                        account.getAccountId()
                                )
                )
                .andExpect(
                        jsonPath(
                                "$.data.withdrawalAccountRegistered"
                        )
                                .value(false)
                );

        entityManager.flush();
        entityManager.clear();

        Account savedAccount =
                findAccount(
                        account.getAccountId(),
                        customerId
                );

        assertThat(
                savedAccount.isWithdrawalRegistered()
        ).isFalse();

        assertThat(
                savedAccount.getWithdrawalRegisteredAt()
        ).isNull();
    }

    @Test
    @DisplayName("이미 미등록된 출금계좌를 삭제해도 성공한다")
    void unregisterAlreadyUnregisteredAccount()
            throws Exception {

        // given
        Long customerId =
                customerTestFixture.createCustomer();

        Account account =
                createAccount(
                        customerId,
                        "088199900032",
                        AccountStatus.ACTIVE
                );

        // when & then
        mockMvc.perform(
                        delete(
                                "/withdrawal-accounts/{accountId}",
                                account.getAccountId()
                        )
                                .with(
                                        authentication(
                                                authenticationOf(
                                                        customerId
                                                )
                                        )
                                )
                                .with(csrf())
                                .header(
                                        "Idempotency-Key",
                                        idempotencyKey()
                                )
                )
                .andExpect(
                        status().isOk()
                )
                .andExpect(
                        jsonPath("$.code")
                                .value("0000")
                )
                .andExpect(
                        jsonPath(
                                "$.data.withdrawalAccountRegistered"
                        )
                                .value(false)
                );
    }

    @Test
    @DisplayName("다른 고객의 출금계좌는 ACC0201을 반환한다")
    void rejectUnregisterOtherCustomersAccount()
            throws Exception {

        // given
        Long ownerCustomerId =
                customerTestFixture.createCustomer();

        Long requesterCustomerId =
                customerTestFixture.createCustomer();

        Account account =
                createRegisteredWithdrawalAccount(
                        ownerCustomerId,
                        "088199900033"
                );

        // when & then
        mockMvc.perform(
                        delete(
                                "/withdrawal-accounts/{accountId}",
                                account.getAccountId()
                        )
                                .with(
                                        authentication(
                                                authenticationOf(
                                                        requesterCustomerId
                                                )
                                        )
                                )
                                .with(csrf())
                                .header(
                                        "Idempotency-Key",
                                        idempotencyKey()
                                )
                )
                .andExpect(
                        status().isNotFound()
                )
                .andExpect(
                        jsonPath("$.code")
                                .value("ACC0201")
                );
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                    "WAITING",
                    "PROCESSING"
            }
    )
    @DisplayName("WAITING 또는 PROCESSING 예약이체가 있으면 출금계좌 삭제를 거부한다")
    void rejectWhenBlockingScheduledTransferExists(
            String status
    ) throws Exception {

        // given
        Long customerId =
                customerTestFixture.createCustomer();

        Account account =
                createRegisteredWithdrawalAccount(
                        customerId,
                        "088199900034"
                );

        jdbcTemplate.update(
                """
                        INSERT INTO scheduled_transfer (
                            customer_id,
                            withdrawal_account_id,
                            payee_bank_code,
                            payee_account_number,
                            payee_name,
                            amount,
                            scheduled_date,
                            status,
                            registered_at
                        )
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                customerId,
                account.getAccountId(),
                "088",
                "088299900011",
                "테스트",
                10_000L,
                "2026-08-21",
                status,
                "2026-08-20 10:00:00"
        );

        // when & then
        mockMvc.perform(
                        delete(
                                "/withdrawal-accounts/{accountId}",
                                account.getAccountId()
                        )
                                .with(
                                        authentication(
                                                authenticationOf(
                                                        customerId
                                                )
                                        )
                                )
                                .with(csrf())
                                .header(
                                        "Idempotency-Key",
                                        idempotencyKey()
                                )
                )
                .andExpect(
                        status().isConflict()
                )
                .andExpect(
                        jsonPath("$.code")
                                .value("ACC0302")
                );

        entityManager.flush();
        entityManager.clear();

        Account savedAccount =
                findAccount(
                        account.getAccountId(),
                        customerId
                );

        assertThat(
                savedAccount.isWithdrawalRegistered()
        ).isTrue();

        assertThat(
                savedAccount.getWithdrawalRegisteredAt()
        ).isNotNull();
    }

    @Test
    @DisplayName("NORMAL 자동이체가 있으면 출금계좌 삭제를 거부한다")
    void rejectWhenNormalAutoTransferExists()
            throws Exception {

        // given
        Long customerId =
                customerTestFixture.createCustomer();

        Account account =
                createRegisteredWithdrawalAccount(
                        customerId,
                        "088199900035"
                );

        jdbcTemplate.update(
                """
                        INSERT INTO auto_transfer (
                            customer_id,
                            withdrawal_account_id,
                            deposit_account_number,
                            payee_name,
                            amount,
                            cycle_months,
                            transfer_day,
                            start_date,
                            end_date,
                            next_execution_date,
                            status,
                            registered_at,
                            updated_at
                        )
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                customerId,
                account.getAccountId(),
                "088299900012",
                "테스트",
                10_000L,
                1,
                20,
                "2026-08-01",
                "2026-12-31",
                "2026-09-20",
                "NORMAL",
                "2026-08-20 10:00:00",
                "2026-08-20 10:00:00"
        );

        // when & then
        mockMvc.perform(
                        delete(
                                "/withdrawal-accounts/{accountId}",
                                account.getAccountId()
                        )
                                .with(
                                        authentication(
                                                authenticationOf(
                                                        customerId
                                                )
                                        )
                                )
                                .with(csrf())
                                .header(
                                        "Idempotency-Key",
                                        idempotencyKey()
                                )
                )
                .andExpect(
                        status().isConflict()
                )
                .andExpect(
                        jsonPath("$.code")
                                .value("ACC0302")
                );

        entityManager.flush();
        entityManager.clear();

        Account savedAccount =
                findAccount(
                        account.getAccountId(),
                        customerId
                );

        assertThat(
                savedAccount.isWithdrawalRegistered()
        ).isTrue();
    }

    @Test
    @DisplayName("같은 Idempotency-Key로 출금계좌 삭제를 재요청해도 성공한다")
    void replaySameUnregisterRequest()
            throws Exception {

        // given
        Long customerId =
                customerTestFixture.createCustomer();

        Account account =
                createRegisteredWithdrawalAccount(
                        customerId,
                        "088199900036"
                );

        String idempotencyKey =
                idempotencyKey();

        // 첫 번째 요청
        mockMvc.perform(
                        delete(
                                "/withdrawal-accounts/{accountId}",
                                account.getAccountId()
                        )
                                .with(
                                        authentication(
                                                authenticationOf(
                                                        customerId
                                                )
                                        )
                                )
                                .with(csrf())
                                .header(
                                        "Idempotency-Key",
                                        idempotencyKey
                                )
                )
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath(
                                "$.data.withdrawalAccountRegistered"
                        )
                                .value(false)
                );

        /*
         * Idempotency bulk update 이후
         * persistence context를 비운다.
         */
        entityManager.flush();
        entityManager.clear();

        // 두 번째 요청
        mockMvc.perform(
                        delete(
                                "/withdrawal-accounts/{accountId}",
                                account.getAccountId()
                        )
                                .with(
                                        authentication(
                                                authenticationOf(
                                                        customerId
                                                )
                                        )
                                )
                                .with(csrf())
                                .header(
                                        "Idempotency-Key",
                                        idempotencyKey
                                )
                )
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath(
                                "$.data.withdrawalAccountRegistered"
                        )
                                .value(false)
                );

        entityManager.flush();
        entityManager.clear();

        Account savedAccount =
                findAccount(
                        account.getAccountId(),
                        customerId
                );

        assertThat(
                savedAccount.isWithdrawalRegistered()
        ).isFalse();

        assertThat(
                savedAccount.getWithdrawalRegisteredAt()
        ).isNull();
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

    private Account createRegisteredWithdrawalAccount(
            Long customerId,
            String accountNumber
    ) {
        Account account =
                createAccount(
                        customerId,
                        accountNumber,
                        AccountStatus.ACTIVE
                );

        account.registerWithdrawalAccount(
                LocalDateTime.of(
                        2026,
                        8,
                        19,
                        14,
                        30
                )
        );

        return accountPersistencePort
                .save(account);
    }

    private String requestJson(
            String accountPasswordAuthToken,
            String otpAuthToken
    ) {
        return """
                {
                  "accountPasswordAuthToken": "%s",
                  "otpAuthToken": "%s"
                }
                """.formatted(
                accountPasswordAuthToken,
                otpAuthToken
        );
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
