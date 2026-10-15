package com.shinhan.corebank.gl.adapter.out.persistence;

import java.math.BigDecimal;

// MySQL 은 BIGINT 의 SUM 을 DECIMAL 로 돌려준다. long 변환은 어댑터가 longValueExact 로 한다.
interface TrialBalanceRowProjection {

    String getAccountCode();

    String getAccountName();

    String getAccountClass();

    String getNormalBalance();

    BigDecimal getDebitTotal();

    BigDecimal getCreditTotal();

    Long getEntryCount();
}
