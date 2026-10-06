package com.shinhan.corebank.gl.domain;

import com.shinhan.corebank.common.exception.BusinessException;
import com.shinhan.corebank.gl.api.JournalDirection;
import com.shinhan.corebank.gl.domain.exception.GlErrorCode;
import java.util.Objects;

/**
 * 분개 한 줄. 금액은 항상 양수이고 방향은 {@code drCr} 이 말한다 — DB 의 {@code CHECK (amount > 0)} 과 같은 불변식이다.
 * 줄 번호는 {@link Voucher} 안의 순서가 정한다.
 */
public record JournalEntry(String accountCode, JournalDirection drCr, long amount) {

    public JournalEntry {
        Objects.requireNonNull(accountCode, "accountCode must not be null");
        Objects.requireNonNull(drCr, "drCr must not be null");
        if (amount <= 0) {
            throw new BusinessException(GlErrorCode.NON_POSITIVE_JOURNAL_AMOUNT);
        }
    }

    public static JournalEntry debit(String accountCode, long amount) {
        return new JournalEntry(accountCode, JournalDirection.DEBIT, amount);
    }

    public static JournalEntry credit(String accountCode, long amount) {
        return new JournalEntry(accountCode, JournalDirection.CREDIT, amount);
    }
}
