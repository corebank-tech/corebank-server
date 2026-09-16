package com.shinhan.corebank.transfer.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.shinhan.corebank.IntegrationTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

@Transactional
class LedgerEntryIdGeneratorTest extends IntegrationTestSupport {

    @Autowired
    private LedgerEntryIdGenerator ledgerEntryIdGenerator;

    @Test
    @DisplayName("nextId()를 호출할 때마다 null이 아닌, 서로 다른 값을 반환한다")
    void nextIdReturnsDistinctNonNullValues() {
        // when
        Long first = ledgerEntryIdGenerator.nextId();
        Long second = ledgerEntryIdGenerator.nextId();

        // then
        assertThat(first).isNotNull();
        assertThat(second).isNotNull();
        assertThat(first).isNotEqualTo(second);
    }

    @Test
    @DisplayName("연속 호출 시 값이 단조 증가한다")
    void nextIdIncreasesMonotonically() {
        // when
        Long first = ledgerEntryIdGenerator.nextId();
        Long second = ledgerEntryIdGenerator.nextId();

        // then
        assertThat(second).isGreaterThan(first);
    }
}
