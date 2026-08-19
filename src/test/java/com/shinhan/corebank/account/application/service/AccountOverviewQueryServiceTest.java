package com.shinhan.corebank.account.application.service;

import com.shinhan.corebank.account.application.port.in.AccountGroupCode;
import com.shinhan.corebank.account.application.port.in.AccountOverviewResult;
import com.shinhan.corebank.account.application.port.out.AccountPersistencePort;
import com.shinhan.corebank.account.domain.Account;
import com.shinhan.corebank.account.domain.AccountStatus;
import com.shinhan.corebank.account.domain.AccountType;
import com.shinhan.corebank.product.application.port.in.ProductQueryUseCase;
import com.shinhan.corebank.product.domain.Product;
import com.shinhan.corebank.product.domain.ProductDetail;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.times;

@ExtendWith(MockitoExtension.class)
@DisplayName("전체 계좌 조회 서비스 단위 테스트")
class AccountOverviewQueryServiceTest {

    private static final Long CUSTOMER_ID = 1L;

    private static final String PASSWORD_HASH =
            "$2a$10$34abEWY4uXLwTEnT5hNow.603a5rWofFx7Bnj59agU.PsESK0v/Yq";

    private static final Clock FIXED_CLOCK =
            Clock.fixed(
                    Instant.parse("2026-08-13T01:30:00Z"),
                    ZoneOffset.UTC
            );

    @Mock
    private AccountPersistencePort accountPersistencePort;

    @Mock
    private ProductQueryUseCase productQueryUseCase;

    private AccountOverviewQueryService service;

    @BeforeEach
    void setUp() {
        service = new AccountOverviewQueryService(
                accountPersistencePort,
                productQueryUseCase,
                FIXED_CLOCK
        );
    }

    @Test
    @DisplayName("보유 계좌가 없으면 전체 자산 0과 빈 목록을 반환한다")
    void returnsEmptyOverviewWhenCustomerHasNoAccounts() {
        // given
        given(accountPersistencePort.findAllByCustomerId(CUSTOMER_ID))
                .willReturn(List.of());

        // when
        AccountOverviewResult result =
                service.getOverview(CUSTOMER_ID);

        // then
        assertThat(result.totalAssets()).isZero();
        assertThat(result.items()).isEmpty();

        assertThat(result.asOf())
                .isEqualTo(
                        OffsetDateTime.ofInstant(
                                FIXED_CLOCK.instant(),
                                ZoneId.of("Asia/Seoul")
                        )
                );

        verifyNoInteractions(productQueryUseCase);
    }

    @Test
    @DisplayName("입출금과 예적금을 상품군으로 묶고 잔액 합계를 계산한다")
    void groupsAccountsAndCalculatesBalances() {
        // given
        Account demandDeposit = createAccount(
                101L,
                "088100000001",
                null,
                AccountType.DEMAND_DEPOSIT,
                1_500_000L,
                AccountStatus.ACTIVE,
                "생활비 통장",
                true
        );

        Account timeDeposit = createAccount(
                102L,
                "088200000001",
                20L,
                AccountType.TIME_DEPOSIT,
                1_000_000L,
                AccountStatus.ACTIVE,
                "내 예금",
                false
        );

        Account savings = createAccount(
                103L,
                "088300000001",
                30L,
                AccountType.INSTALLMENT_SAVINGS,
                1_000_000L,
                AccountStatus.ACTIVE,
                "내 적금",
                false
        );

        given(accountPersistencePort.findAllByCustomerId(CUSTOMER_ID))
                .willReturn(List.of(
                        demandDeposit,
                        timeDeposit,
                        savings
                ));

        // when
        AccountOverviewResult result =
                service.getOverview(CUSTOMER_ID);

        // then
        assertThat(result.totalAssets())
                .isEqualTo(3_500_000L);

        assertThat(result.items()).hasSize(2);

        AccountOverviewResult.Group demandGroup =
                result.items().get(0);

        assertThat(demandGroup.groupCode())
                .isEqualTo(AccountGroupCode.DEMAND_DEPOSIT);

        assertThat(demandGroup.groupName())
                .isEqualTo("입출금계좌");

        assertThat(demandGroup.groupTotalBalance())
                .isEqualTo(1_500_000L);

        assertThat(demandGroup.accounts()).hasSize(1);

        AccountOverviewResult.Group savingsGroup =
                result.items().get(1);

        assertThat(savingsGroup.groupCode())
                .isEqualTo(AccountGroupCode.DEPOSIT_SAVINGS);

        assertThat(savingsGroup.groupName())
                .isEqualTo("예금·적금");

        assertThat(savingsGroup.groupTotalBalance())
                .isEqualTo(2_000_000L);

        assertThat(savingsGroup.accounts()).hasSize(2);
    }

    @Test
    @DisplayName("별명이 없는 입출금계좌는 입출금통장으로 표시한다")
    void usesDefaultDemandDepositNameWhenAliasDoesNotExist() {
        // given
        Account account = createAccount(
                101L,
                "088100000001",
                null,
                AccountType.DEMAND_DEPOSIT,
                100_000L,
                AccountStatus.ACTIVE,
                null,
                false
        );

        given(accountPersistencePort.findAllByCustomerId(CUSTOMER_ID))
                .willReturn(List.of(account));

        // when
        AccountOverviewResult result =
                service.getOverview(CUSTOMER_ID);

        // then
        assertThat(
                result.items()
                        .get(0)
                        .accounts()
                        .get(0)
                        .accountName()
        ).isEqualTo("입출금통장");

        verifyNoInteractions(productQueryUseCase);
    }

    @Test
    @DisplayName("별명이 있는 계좌는 상품명보다 별명을 우선한다")
    void usesAliasBeforeProductName() {
        // given
        Account account = createAccount(
                102L,
                "088200000001",
                20L,
                AccountType.TIME_DEPOSIT,
                1_000_000L,
                AccountStatus.ACTIVE,
                "내 목돈",
                false
        );

        given(accountPersistencePort.findAllByCustomerId(CUSTOMER_ID))
                .willReturn(List.of(account));

        // when
        AccountOverviewResult result =
                service.getOverview(CUSTOMER_ID);

        // then
        assertThat(
                result.items()
                        .get(0)
                        .accounts()
                        .get(0)
                        .accountName()
        ).isEqualTo("내 목돈");

        verify(productQueryUseCase, never())
                .getDetail(20L);
    }

    @Test
    @DisplayName("별명이 없는 예적금계좌는 상품명을 표시한다")
    void usesProductNameWhenSavingsAccountHasNoAlias() {
        // given
        Account account = createAccount(
                103L,
                "088300000001",
                30L,
                AccountType.INSTALLMENT_SAVINGS,
                1_000_000L,
                AccountStatus.ACTIVE,
                null,
                false
        );

        ProductDetail productDetail =
                mock(ProductDetail.class);

        Product product =
                mock(Product.class);

        given(accountPersistencePort.findAllByCustomerId(CUSTOMER_ID))
                .willReturn(List.of(account));

        given(productQueryUseCase.getDetail(30L))
                .willReturn(productDetail);

        given(productDetail.getProduct())
                .willReturn(product);

        given(product.getProductName())
                .willReturn("청년 희망 적금");

        // when
        AccountOverviewResult result =
                service.getOverview(CUSTOMER_ID);

        // then
        assertThat(
                result.items()
                        .get(0)
                        .accounts()
                        .get(0)
                        .accountName()
        ).isEqualTo("청년 희망 적금");
    }

    @Test
    @DisplayName("활성 상태의 출금계좌로 등록된 입출금계좌만 이체가 가능하다")
    void enablesTransferOnlyForEligibleDemandDepositAccount() {
        // given
        Account enabled = createAccount(
                101L,
                "088100000001",
                null,
                AccountType.DEMAND_DEPOSIT,
                100_000L,
                AccountStatus.ACTIVE,
                "계좌1",
                true
        );

        Account notRegistered = createAccount(
                102L,
                "088100000002",
                null,
                AccountType.DEMAND_DEPOSIT,
                100_000L,
                AccountStatus.ACTIVE,
                "계좌2",
                false
        );

        Account suspended = createAccount(
                103L,
                "088100000003",
                null,
                AccountType.DEMAND_DEPOSIT,
                100_000L,
                AccountStatus.SUSPENDED,
                "계좌3",
                true
        );

        Account timeDeposit = createAccount(
                104L,
                "088200000001",
                20L,
                AccountType.TIME_DEPOSIT,
                100_000L,
                AccountStatus.ACTIVE,
                "예금",
                false
        );

        given(accountPersistencePort.findAllByCustomerId(CUSTOMER_ID))
                .willReturn(List.of(
                        enabled,
                        notRegistered,
                        suspended,
                        timeDeposit
                ));

        // when
        AccountOverviewResult result =
                service.getOverview(CUSTOMER_ID);

        // then
        List<AccountOverviewResult.AccountItem> demandAccounts =
                result.items().stream()
                        .filter(group ->
                                group.groupCode()
                                        == AccountGroupCode.DEMAND_DEPOSIT)
                        .findFirst()
                        .orElseThrow()
                        .accounts();

        assertThat(demandAccounts.get(0).transferEnabled())
                .isTrue();

        assertThat(demandAccounts.get(1).transferEnabled())
                .isFalse();

        assertThat(demandAccounts.get(2).transferEnabled())
                .isFalse();

        List<AccountOverviewResult.AccountItem> savingsAccounts =
                result.items().stream()
                        .filter(group ->
                                group.groupCode()
                                        == AccountGroupCode.DEPOSIT_SAVINGS)
                        .findFirst()
                        .orElseThrow()
                        .accounts();

        assertThat(savingsAccounts.get(0).transferEnabled())
                .isFalse();
    }

    @Test
    @DisplayName("동일 상품 계좌가 여러 개여도 상품명은 한 번만 조회한다")
    void cachesProductNameWithinRequest() {
        // given
        Account firstAccount = createAccount(
                102L,
                "088200000001",
                20L,
                AccountType.TIME_DEPOSIT,
                1_000_000L,
                AccountStatus.ACTIVE,
                null,
                false
        );

        Account secondAccount = createAccount(
                103L,
                "088200000002",
                20L,
                AccountType.TIME_DEPOSIT,
                2_000_000L,
                AccountStatus.ACTIVE,
                null,
                false
        );

        ProductDetail productDetail =
                mock(ProductDetail.class);

        Product product =
                mock(Product.class);

        given(accountPersistencePort
                .findAllByCustomerId(CUSTOMER_ID))
                .willReturn(List.of(
                        firstAccount,
                        secondAccount
                ));

        given(productQueryUseCase.getDetail(20L))
                .willReturn(productDetail);

        given(productDetail.getProduct())
                .willReturn(product);

        given(product.getProductName())
                .willReturn("신한 정기예금");

        // when
        AccountOverviewResult result =
                service.getOverview(CUSTOMER_ID);

        // then
        assertThat(
                result.items()
                        .get(0)
                        .accounts()
        )
                .extracting(
                        AccountOverviewResult
                                .AccountItem::accountName
                )
                .containsExactly(
                        "신한 정기예금",
                        "신한 정기예금"
                );

        verify(productQueryUseCase, times(1))
                .getDetail(20L);
    }

    @Test
    @DisplayName("해지 계좌는 전체 계좌 조회와 자산 합계에서 제외하고 정지 계좌는 포함한다")
    void excludeClosedAccountFromOverview() {
        // given
        Long customerId = 1L;

        Account activeAccount =
                createAccountWithStatus(
                        101L,
                        "088100000101",
                        customerId,
                        10_000L,
                        AccountStatus.ACTIVE,
                        LocalDateTime.of(
                                2026, 8, 1, 10, 0
                        ),
                        null
                );

        Account suspendedAccount =
                createAccountWithStatus(
                        102L,
                        "088100000102",
                        customerId,
                        20_000L,
                        AccountStatus.SUSPENDED,
                        LocalDateTime.of(
                                2026, 8, 2, 10, 0
                        ),
                        null
                );

        Account closedAccount =
                createAccountWithStatus(
                        103L,
                        "088100000103",
                        customerId,
                        30_000L,
                        AccountStatus.CLOSED,
                        LocalDateTime.of(
                                2026, 8, 3, 10, 0
                        ),
                        LocalDateTime.of(
                                2026, 8, 10, 10, 0
                        )
                );

        given(
                accountPersistencePort
                        .findAllByCustomerId(
                                customerId
                        )
        ).willReturn(
                List.of(
                        activeAccount,
                        suspendedAccount,
                        closedAccount
                )
        );

        // when
        AccountOverviewResult result =
                service.getOverview(
                                customerId
                        );

        // then
        List<Long> accountIds =
                result.items()
                        .stream()
                        .flatMap(group ->
                                group.accounts()
                                        .stream()
                        )
                        .map(account ->
                                account.accountId()
                        )
                        .toList();

        assertThat(accountIds)
                .containsExactlyInAnyOrder(
                        101L,
                        102L
                );

        assertThat(accountIds)
                .doesNotContain(
                        103L
                );

        /*
         * ACTIVE 10,000
         * + SUSPENDED 20,000
         * = 30,000
         *
         * CLOSED 30,000은 제외한다.
         */
        assertThat(
                result.totalAssets()
        ).isEqualTo(
                30_000L
        );

        long groupTotalBalance =
                result.items()
                        .stream()
                        .mapToLong(group ->
                                group.groupTotalBalance()
                        )
                        .sum();

        assertThat(
                groupTotalBalance
        ).isEqualTo(
                30_000L
        );
    }

    private Account createAccount(
            Long accountId,
            String accountNumber,
            Long productId,
            AccountType accountType,
            long balance,
            AccountStatus status,
            String alias,
            boolean withdrawalRegistered
    ) {
        LocalDateTime openedDate =
                LocalDateTime.of(2026, 1, 1, 10, 0);

        LocalDate maturityDate =
                accountType == AccountType.DEMAND_DEPOSIT
                        ? null
                        : LocalDate.of(2027, 1, 1);

        LocalDateTime closedDate =
                status == AccountStatus.CLOSED
                        ? LocalDateTime.of(
                        2026, 8, 12, 10, 0
                )
                        : null;

        LocalDateTime withdrawalRegisteredAt =
                withdrawalRegistered
                        ? LocalDateTime.of(2026, 1, 2, 10, 0)
                        : null;

        return Account.reconstitute(
                accountId,
                accountNumber,
                CUSTOMER_ID,
                productId,
                accountType,
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
                maturityDate,
                closedDate,
                LocalDateTime.of(2026, 8, 10, 15, 0),
                0L,
                openedDate,
                openedDate
        );
    }

    private Account createAccountWithStatus(
            Long accountId,
            String accountNumber,
            Long customerId,
            Long balance,
            AccountStatus status,
            LocalDateTime openedDate,
            LocalDateTime closedDate
    ) {
        return Account.reconstitute(
                accountId,
                accountNumber,
                customerId,
                null,
                AccountType.DEMAND_DEPOSIT,
                balance,
                status,
                PASSWORD_HASH,
                0,
                false,
                null,
                null,
                false,
                null,
                openedDate,
                null,
                closedDate,
                null,
                0L,
                openedDate,
                openedDate
        );
    }

}