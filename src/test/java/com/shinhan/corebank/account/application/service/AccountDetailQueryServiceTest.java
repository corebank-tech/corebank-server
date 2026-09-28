package com.shinhan.corebank.account.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

import com.shinhan.corebank.account.application.port.in.AccountDetailResult;
import com.shinhan.corebank.account.application.port.out.AccountPersistencePort;
import com.shinhan.corebank.account.domain.Account;
import com.shinhan.corebank.account.domain.AccountStatus;
import com.shinhan.corebank.account.domain.AccountType;
import com.shinhan.corebank.account.domain.exception.AccountErrorCode;
import com.shinhan.corebank.common.exception.BusinessException;
import com.shinhan.corebank.product.application.port.in.ProductQueryUseCase;
import com.shinhan.corebank.product.domain.exception.ProductErrorCode;
import java.time.*;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

// 계좌상세 조회의 소유권과 출금 가능 잔액 계산 정책을 검증한다.
class AccountDetailQueryServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-22T01:00:00Z");

    private final AccountPersistencePort persistencePort = mock(AccountPersistencePort.class);
    private final ProductQueryUseCase productQueryUseCase = mock(ProductQueryUseCase.class);
    private final AccountDetailQueryService service =
            new AccountDetailQueryService(persistencePort, productQueryUseCase, Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    @DisplayName("활성·미잠금 계좌는 잔액 전액을 출금 가능 잔액으로 반환한다")
    void returnsBalanceAsAvailableBalance() {
        Account account = account(AccountStatus.ACTIVE, 0, false, "급여통장");
        given(persistencePort.findByAccountIdAndCustomerId(101L, 1L)).willReturn(Optional.of(account));

        AccountDetailResult result = service.getDetail(1L, 101L);

        assertThat(result.accountName()).isEqualTo("급여통장");
        assertThat(result.balance()).isEqualTo(1_500_000L);
        assertThat(result.availableBalance()).isEqualTo(1_500_000L);
        assertThat(result.asOf().getOffset()).isEqualTo(ZoneOffset.ofHours(9));
        verifyNoInteractions(productQueryUseCase);
    }

    @Test
    @DisplayName("비밀번호 잠금 계좌는 출금 가능 잔액을 0으로 반환한다")
    void lockedAccountHasNoAvailableBalance() {
        Account account = account(AccountStatus.ACTIVE, 5, true, null);
        given(persistencePort.findByAccountIdAndCustomerId(101L, 1L)).willReturn(Optional.of(account));

        AccountDetailResult result = service.getDetail(1L, 101L);

        assertThat(result.accountName()).isEqualTo("입출금통장");
        assertThat(result.availableBalance()).isZero();
        assertThat(result.passwordFailureCount()).isEqualTo(5);
        assertThat(result.passwordLocked()).isTrue();
    }

    @Test
    @DisplayName("존재하지 않거나 타인 소유인 계좌는 ACC0201을 반환한다")
    void rejectsMissingOrOtherCustomersAccount() {
        given(persistencePort.findByAccountIdAndCustomerId(101L, 1L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.getDetail(1L, 101L))
                .isInstanceOfSatisfying(BusinessException.class, exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(AccountErrorCode.ACCOUNT_NOT_FOUND_OR_FORBIDDEN));
    }

    @Test
    @DisplayName("예적금 계좌는 상품명 전용 조회 결과를 계좌명으로 반환한다")
    void usesLightweightProductNameQueryForProductAccount() {
        // given
        Account account = productAccount(AccountType.TIME_DEPOSIT, 20L, null);

        given(persistencePort.findByAccountIdAndCustomerId(101L, 1L)).willReturn(Optional.of(account));

        given(productQueryUseCase.findProductNames(Set.of(20L))).willReturn(Map.of(20L, "신한 정기예금"));

        // when
        AccountDetailResult result = service.getDetail(1L, 101L);

        // then
        assertThat(result.accountName()).isEqualTo("신한 정기예금");

        verify(productQueryUseCase).findProductNames(Set.of(20L));
        verify(productQueryUseCase, never()).getDetail(20L);
    }

    @Test
    @DisplayName("예적금 계좌의 상품명이 조회되지 않으면 PRD0201을 발생시킨다")
    void rejectsWhenProductNameIsMissing() {
        // given
        Account account = productAccount(AccountType.INSTALLMENT_SAVINGS, 30L, null);

        given(persistencePort.findByAccountIdAndCustomerId(101L, 1L)).willReturn(Optional.of(account));

        given(productQueryUseCase.findProductNames(Set.of(30L))).willReturn(Map.of());

        // when & then
        assertThatThrownBy(() -> service.getDetail(1L, 101L))
                .isInstanceOfSatisfying(BusinessException.class, exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(ProductErrorCode.PRODUCT_NOT_FOUND));

        verify(productQueryUseCase).findProductNames(Set.of(30L));
        verify(productQueryUseCase, never()).getDetail(30L);
    }

    @Test
    @DisplayName("예적금 계좌에 별명이 있으면 상품 조회 없이 별명을 반환한다")
    void usesAliasWithoutProductLookup() {
        // given
        Account account = productAccount(AccountType.TIME_DEPOSIT, 20L, "내 목돈");

        given(persistencePort.findByAccountIdAndCustomerId(101L, 1L)).willReturn(Optional.of(account));

        // when
        AccountDetailResult result = service.getDetail(1L, 101L);

        // then
        assertThat(result.accountName()).isEqualTo("내 목돈");
        verifyNoInteractions(productQueryUseCase);
    }

    private Account account(AccountStatus status, int failureCount, boolean locked, String alias) {
        return Account.reconstitute(
                101L,
                "110550051877",
                1L,
                null,
                AccountType.DEMAND_DEPOSIT,
                1_500_000L,
                status,
                "$2a$10$34abEWY4uXLwTEnT5hNow.603a5rWofFx7Bnj59agU.PsESK0v/Yq",
                failureCount,
                locked,
                alias,
                1,
                true,
                LocalDateTime.of(2026, 8, 2, 1, 0),
                LocalDateTime.of(2025, 3, 10, 0, 0),
                null,
                null,
                null,
                0L,
                LocalDateTime.of(2025, 3, 10, 0, 0),
                LocalDateTime.of(2026, 8, 20, 0, 0));
    }

    private Account productAccount(AccountType accountType, Long productId, String alias) {

        return Account.reconstitute(
                101L,
                "110550051877",
                1L,
                productId,
                accountType,
                1_500_000L,
                AccountStatus.ACTIVE,
                "$2a$10$34abEWY4uXLwTEnT5hNow.603a5rWofFx7Bnj59agU.PsESK0v/Yq",
                0,
                false,
                alias,
                1,
                false, // 예·적금은 출금계좌 등록 불가
                null, // 따라서 등록 시각도 없음
                LocalDateTime.of(2025, 3, 10, 0, 0),
                LocalDate.of(2026, 3, 10), // 예·적금은 만기일 필수
                null,
                null,
                0L,
                LocalDateTime.of(2025, 3, 10, 0, 0),
                LocalDateTime.of(2026, 8, 20, 0, 0));
    }
}
