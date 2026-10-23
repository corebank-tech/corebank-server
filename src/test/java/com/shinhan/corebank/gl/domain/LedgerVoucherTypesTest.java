package com.shinhan.corebank.gl.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.shinhan.corebank.gl.api.GlTxType;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

class LedgerVoucherTypesTest {

    @ParameterizedTest(name = "{0} → {1}")
    @CsvSource({
        "IMMEDIATE_TRANSFER, TRANSFER",
        "SCHEDULED_TRANSFER, TRANSFER",
        "AUTO_TRANSFER, TRANSFER",
        "PRODUCT_SUBSCRIPTION, PRODUCT_SUBSCRIPTION",
        "REVERSAL, REVERSAL"
    })
    void mapsHookPostedTypes(String ledgerType, GlTxType voucherType) {
        assertThat(LedgerVoucherTypes.voucherTypeOf(ledgerType)).hasValue(voucherType);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"INTERBANK_TRANSFER", "INTEREST", "MATURITY", "TERMINATION", "OPENING", "QA_SEED_INITIAL"})
    void otherTypesAreNotReconciled(String ledgerType) {
        assertThat(LedgerVoucherTypes.voucherTypeOf(ledgerType)).isEmpty();
    }
}
