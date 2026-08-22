package com.shinhan.corebank.account.application.service;

import com.shinhan.corebank.account.application.port.in.AccountDetailQueryUseCase;
import com.shinhan.corebank.account.application.port.in.AccountDetailResult;
import com.shinhan.corebank.account.application.port.out.AccountPersistencePort;
import com.shinhan.corebank.account.domain.Account;
import com.shinhan.corebank.account.domain.AccountStatus;
import com.shinhan.corebank.account.domain.AccountType;
import com.shinhan.corebank.account.domain.exception.AccountErrorCode;
import com.shinhan.corebank.common.exception.BusinessException;
import com.shinhan.corebank.product.application.port.in.ProductQueryUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneId;

// 로그인 고객의 소유권을 확인한 뒤 계좌상세 정보를 조회한다.
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AccountDetailQueryService
        implements AccountDetailQueryUseCase {

    private static final ZoneId KOREA_ZONE =
            ZoneId.of("Asia/Seoul");
    private static final String DEFAULT_DEMAND_DEPOSIT_NAME =
            "입출금통장";

    private final AccountPersistencePort accountPersistencePort;
    private final ProductQueryUseCase productQueryUseCase;
    private final Clock clock;

    @Override
    public AccountDetailResult getDetail(
            Long customerId,
            Long accountId
    ) {
        Account account = accountPersistencePort
                .findByAccountIdAndCustomerId(
                        accountId,
                        customerId
                )
                .orElseThrow(() -> new BusinessException(
                        AccountErrorCode.ACCOUNT_NOT_FOUND_OR_FORBIDDEN
                ));

        return new AccountDetailResult(
                OffsetDateTime.ofInstant(
                        clock.instant(),
                        KOREA_ZONE
                ),
                account.getAccountId(),
                resolveAccountName(account),
                account.getAccountNumber(),
                account.getBalance(),
                resolveAvailableBalance(account),
                account.getOpenedDate().toLocalDate(),
                account.getStatus(),
                account.getPasswordFailureCount(),
                account.isPasswordLocked()
        );
    }

    // 별명이 없으면 입출금 기본명 또는 연결된 상품명을 사용한다.
    private String resolveAccountName(Account account) {
        if (account.getAlias() != null
                && !account.getAlias().isBlank()) {
            return account.getAlias();
        }

        if (account.getAccountType()
                == AccountType.DEMAND_DEPOSIT) {
            return DEFAULT_DEMAND_DEPOSIT_NAME;
        }

        return productQueryUseCase
                .getDetail(account.getProductId())
                .getProduct()
                .getProductName();
    }

    // 거래정지·해지·비밀번호 잠금 계좌의 출금 가능 잔액을 0으로 반환한다.
    private long resolveAvailableBalance(Account account) {
        boolean withdrawalRestricted =
                account.getStatus() != AccountStatus.ACTIVE
                        || account.isPasswordLocked();

        return withdrawalRestricted
                ? 0L
                : account.getBalance();
    }
}
