package com.shinhan.corebank.gl.adapter.out.persistence;

import com.shinhan.corebank.gl.application.port.out.VoucherSavePort;
import com.shinhan.corebank.gl.domain.Voucher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class GlVoucherPersistenceAdapter implements VoucherSavePort {

    private final GlVoucherJpaRepository voucherRepository;
    private final GlJournalEntryJpaRepository journalEntryRepository;

    public GlVoucherPersistenceAdapter(
            GlVoucherJpaRepository voucherRepository, GlJournalEntryJpaRepository journalEntryRepository) {
        this.voucherRepository = voucherRepository;
        this.journalEntryRepository = journalEntryRepository;
    }

    // 전표와 분개 줄이 함께 들어가거나 함께 빠지는 것을 호출자의 트랜잭션 유무와 무관하게 보장한다.
    @Override
    @Transactional
    public void save(Voucher voucher) {
        // 전표를 먼저 flush 한다. 분개는 IDENTITY 라 save 즉시 INSERT 되는데, 전표가 아직 쓰기 지연 상태면 FK 위반이다.
        voucherRepository.saveAndFlush(GlVoucherMapper.toVoucherEntity(voucher));
        journalEntryRepository.saveAll(GlVoucherMapper.toJournalEntryEntities(voucher));
    }
}
