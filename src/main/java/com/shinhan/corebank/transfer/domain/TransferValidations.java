package com.shinhan.corebank.transfer.domain;

import com.shinhan.corebank.common.exception.BusinessException;
import com.shinhan.corebank.common.exception.CommonErrorCode;
import com.shinhan.corebank.common.exception.ErrorCode;
import com.shinhan.corebank.transfer.domain.exception.TransferErrorCode;

/**
 * {@link Transfer#create}와 {@link LedgerPair#forTransfer}에서 공통으로 쓰는 입력값 검증.
 * 두 팩토리에 각각 흩어져 있던 동일한 검증(계좌 null, 금액 양수, 동일계좌)을
 * 한 곳으로 모아 정책 변경 시 한쪽만 고치는 drift를 방지한다.
 */
final class TransferValidations {

    private TransferValidations() {
    }

    static void requireNonNull(Object value, ErrorCode errorCode) {
        if (value == null) {
            throw new BusinessException(errorCode);
        }
    }

    static void requireNonBlank(String value, ErrorCode errorCode) {
        if (value == null || value.isBlank()) {
            throw new BusinessException(errorCode);
        }
    }

    static void requirePositiveAmount(long amount) {
        if (amount <= 0) {
            throw new BusinessException(TransferErrorCode.INVALID_AMOUNT);
        }
    }

    static void requireNonNegativeFee(long fee) {
        if (fee < 0) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT);
        }
    }

    static void requireDifferentAccounts(Long withdrawalAccountId, Long depositAccountId) {
        if (withdrawalAccountId.equals(depositAccountId)) {
            throw new BusinessException(TransferErrorCode.SAME_ACCOUNT_TRANSFER);
        }
    }

    static void requireAccountIdsPresent(Long withdrawalAccountId, Long depositAccountId) {
        requireNonNull(withdrawalAccountId, CommonErrorCode.REQUIRED_FIELD_MISSING);
        requireNonNull(depositAccountId, CommonErrorCode.REQUIRED_FIELD_MISSING);
    }
}
