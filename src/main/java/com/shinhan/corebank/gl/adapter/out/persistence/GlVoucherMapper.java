package com.shinhan.corebank.gl.adapter.out.persistence;

import com.shinhan.corebank.gl.domain.JournalEntry;
import com.shinhan.corebank.gl.domain.Voucher;
import java.util.ArrayList;
import java.util.List;

final class GlVoucherMapper {

    private GlVoucherMapper() {}

    static GlVoucherJpaEntity toVoucherEntity(Voucher voucher) {
        return GlVoucherJpaEntity.of(
                voucher.getNumber().value(), voucher.getTradeDate(), voucher.getTxType(), voucher.getDescription());
    }

    /** 줄 번호는 전표 안의 순서로 1부터 매긴다. 분개의 {@code trade_date} 는 전표 값을 복제한다(복합 FK). */
    static List<GlJournalEntryJpaEntity> toJournalEntryEntities(Voucher voucher) {
        String voucherNo = voucher.getNumber().value();
        List<JournalEntry> entries = voucher.getEntries();
        List<GlJournalEntryJpaEntity> entities = new ArrayList<>(entries.size());
        for (int i = 0; i < entries.size(); i++) {
            JournalEntry entry = entries.get(i);
            entities.add(GlJournalEntryJpaEntity.of(
                    voucherNo,
                    (short) (i + 1),
                    entry.accountCode(),
                    entry.drCr(),
                    entry.amount(),
                    voucher.getTradeDate()));
        }
        return entities;
    }
}
