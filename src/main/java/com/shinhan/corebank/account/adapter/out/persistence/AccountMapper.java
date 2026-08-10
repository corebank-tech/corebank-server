package com.shinhan.corebank.account.adapter.out.persistence;

import com.shinhan.corebank.account.domain.Account;

public final class AccountMapper {

    private AccountMapper() {
    }

    public static AccountJpaEntity toEntity(Account domain) {
        return AccountJpaEntity.builder()
                .accountId(domain.getAccountId())
                .accountNumber(domain.getAccountNumber())
                .customerId(domain.getCustomerId())
                .productId(domain.getProductId())
                .accountType(domain.getAccountType())
                .balance(domain.getBalance())
                .status(domain.getStatus())
                .passwordHash(domain.getPasswordHash())
                .passwordFailureCount((byte)domain.getPasswordFailureCount())
                .passwordLocked(domain.isPasswordLocked())
                .alias(domain.getAlias())
                .displayOrder(domain.getDisplayOrder())
                .withdrawalRegistered(domain.isWithdrawalRegistered())
                .withdrawalRegisteredAt(domain.getWithdrawalRegisteredAt())
                .openedDate(domain.getOpenedDate())
                .maturityDate(domain.getMaturityDate())
                .closedDate(domain.getClosedDate())
                .lastTransactionAt(domain.getLastTransactionAt())
                .version(domain.getVersion())
                .build();
    }

    public static Account toDomain(AccountJpaEntity entity) {
        return Account.reconstitute(
                entity.getAccountId(),
                entity.getAccountNumber(),
                entity.getCustomerId(),
                entity.getProductId(),
                entity.getAccountType(),
                entity.getBalance(),
                entity.getStatus(),
                entity.getPasswordHash(),
                entity.getPasswordFailureCount(),
                entity.isPasswordLocked(),
                entity.getAlias(),
                entity.getDisplayOrder(),
                entity.isWithdrawalRegistered(),
                entity.getWithdrawalRegisteredAt(),
                entity.getOpenedDate(),
                entity.getMaturityDate(),
                entity.getClosedDate(),
                entity.getLastTransactionAt(),
                entity.getVersion(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }
}