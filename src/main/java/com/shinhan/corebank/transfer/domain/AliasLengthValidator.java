package com.shinhan.corebank.transfer.domain;

import com.shinhan.corebank.common.exception.BusinessException;
import com.shinhan.corebank.transfer.domain.exception.FavoriteAccountErrorCode;

public final class AliasLengthValidator {

    private static final int MAX_WEIGHT = 24;
    private static final int KOREAN_WEIGHT = 2;
    private static final int OTHER_WEIGHT = 1;

    private AliasLengthValidator() {}

    public static void validate(String alias) {
        int weight = alias.codePoints()
                .map(codePoint -> isKoreanSyllable(codePoint) ? KOREAN_WEIGHT : OTHER_WEIGHT)
                .sum();
        if (weight > MAX_WEIGHT) {
            throw new BusinessException(FavoriteAccountErrorCode.ALIAS_LENGTH_EXCEEDED);
        }
    }

    private static boolean isKoreanSyllable(int codePoint) {
        return codePoint >= 0xAC00 && codePoint <= 0xD7A3;
    }
}
