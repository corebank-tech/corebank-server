package com.shinhan.corebank.gl.domain;

/**
 * 분개 한 줄의 방향. 금액은 항상 양수이고 차변인지 대변인지는 이 값이 말한다.
 *
 * <p>Apache Fineract {@code acc_gl_journal_entry.type_enum} 에 대응한다.
 */
public enum JournalDirection {
    DEBIT,
    CREDIT
}
