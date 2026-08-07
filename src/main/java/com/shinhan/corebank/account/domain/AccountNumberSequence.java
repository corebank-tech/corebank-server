package com.shinhan.corebank.account.domain;

import lombok.Getter;

import java.util.Locale;

@Getter
public class AccountNumberSequence {
    private final Long sequenceId;
    private final String bankCode;
    private final AccountType accountType;
    private final Long productId;
    private final String productPrefix;

    private long lastSequence;


    private AccountNumberSequence(
            Long sequenceId,
            String bankCode,
            AccountType accountType,
            Long productId,
            String productPrefix,
            long lastSequence
    ) {
        this.sequenceId = sequenceId;
        this.bankCode = bankCode;
        this.accountType = accountType;
        this.productId = productId;
        this.productPrefix = productPrefix;
        this.lastSequence = lastSequence;
    }

    public static AccountNumberSequence reconstitute(
            Long sequenceId,
            String bankCode,
            AccountType accountType,
            Long productId,
            String productPrefix,
            long lastSequence
    ) {
        validateState(
                sequenceId,
                bankCode,
                accountType,
                productId,
                productPrefix,
                lastSequence
        );

        return new AccountNumberSequence(
                sequenceId,
                bankCode,
                accountType,
                productId,
                productPrefix,
                lastSequence
        );
    }

    public boolean isExhausted() {
        return lastSequence >= AccountNumberPolicy.MAX_SEQUENCE;
    }

    public String issueNext() {
        if (isExhausted()) {
            throw new IllegalStateException(
                    "계좌번호 일련번호가 소진되었습니다."
            );
        }

        long nextSequence = lastSequence + 1;
        String sequencePart = String.format(
                Locale.ROOT,    //Locale.ROOT 적용
                "%0" + AccountNumberPolicy.SEQUENCE_LENGTH + "d",
                nextSequence
        );

        String accountNumber = bankCode + productPrefix + sequencePart;
        validateGeneratedAccountNumber(accountNumber);
        this.lastSequence = nextSequence;
        return accountNumber;
    }

    private static void validateState(
            Long sequenceId,
            String bankCode,
            AccountType accountType,
            Long productId,
            String productPrefix,
            long lastSequence
    ) {
        if (sequenceId == null || sequenceId <= 0) {
            throw new IllegalStateException(
                    "채번 식별자가 올바르지 않습니다."
            );
        }
        if (bankCode == null || !bankCode.matches("^[0-9]{3}$")) {
            throw new IllegalStateException(
                    "사전 정의된 은행코드가 숫자 3자리가 아닙니다."
            );
        }
        if (accountType == null) {
            throw new IllegalStateException(
                    "사전 정의된 계좌 유형이 없습니다."
            );
        }
        if (productPrefix == null || !productPrefix.matches("^[0-9]{2}$")) {
            throw new IllegalStateException(
                    "사전 정의된 상품 prefix가 숫자 2자리가 아닙니다."
            );
        }
        if (lastSequence < 0
                || lastSequence > AccountNumberPolicy.MAX_SEQUENCE) {
            throw new IllegalStateException(
                    "계좌번호 일련번호가 올바르지 않습니다."
            );
        }
        boolean demandDeposit =
                accountType == AccountType.DEMAND_DEPOSIT;
        boolean invalidProductCombination =
                (demandDeposit && productId != null) || (!demandDeposit && productId == null);
        if(invalidProductCombination){
            throw new IllegalStateException(
                    "계좌 유형과 상품 ID 조합이 올바르지 않습니다."
            );
        }
    }

    private static void validateGeneratedAccountNumber(String accountNumber){
        if(accountNumber.length() != AccountNumberPolicy.ACCOUNT_NUMBER_LENGTH){
            throw new IllegalStateException(
                    "생성된 계좌번호 길이가 올바르지 않습니다."
            );
        }
        if(!accountNumber.matches("^[0-9]{12}$")){
            throw new IllegalStateException(
                    "생성된 계좌번호가 숫자 12자리가 아닙니다."
            );
        }
    }
}
