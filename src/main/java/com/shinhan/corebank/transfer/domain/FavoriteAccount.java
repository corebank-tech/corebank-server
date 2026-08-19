package com.shinhan.corebank.transfer.domain;

import java.time.LocalDateTime;

public class FavoriteAccount {

    private final Long favoriteAccountId;
    private final Long customerId;
    private final String depositAccountNumber;
    private final String payeeName;
    private final String alias;
    private final LocalDateTime registeredAt;

    private FavoriteAccount(Long favoriteAccountId, Long customerId, String depositAccountNumber,
                             String payeeName, String alias, LocalDateTime registeredAt) {
        this.favoriteAccountId = favoriteAccountId;
        this.customerId = customerId;
        this.depositAccountNumber = depositAccountNumber;
        this.payeeName = payeeName;
        this.alias = alias;
        this.registeredAt = registeredAt;
    }

    public static FavoriteAccount register(Long customerId, String depositAccountNumber, String payeeName,
                                            String rawAlias, LocalDateTime registeredAt) {
        // 별칭 미지정 시 예금주명이 그대로 기본 별칭이 되므로, 예금주명도 FAV0001 검증 대상이다 (마이그레이션 주석 참고)
        String alias = (rawAlias == null || rawAlias.isBlank()) ? payeeName : rawAlias;
        AliasLengthValidator.validate(alias);
        return new FavoriteAccount(null, customerId, depositAccountNumber, payeeName, alias, registeredAt);
    }

    public static FavoriteAccount of(Long favoriteAccountId, Long customerId, String depositAccountNumber,
                                      String payeeName, String alias, LocalDateTime registeredAt) {
        return new FavoriteAccount(favoriteAccountId, customerId, depositAccountNumber, payeeName, alias, registeredAt);
    }

    public Long getFavoriteAccountId() {
        return favoriteAccountId;
    }

    public Long getCustomerId() {
        return customerId;
    }

    public String getDepositAccountNumber() {
        return depositAccountNumber;
    }

    public String getPayeeName() {
        return payeeName;
    }

    public String getAlias() {
        return alias;
    }

    public LocalDateTime getRegisteredAt() {
        return registeredAt;
    }
}
