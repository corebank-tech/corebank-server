package com.shinhan.corebank.gl.adapter.out.persistence;

import com.shinhan.corebank.common.exception.BusinessException;
import com.shinhan.corebank.gl.application.port.out.VoucherSavePort;
import com.shinhan.corebank.gl.domain.Voucher;
import com.shinhan.corebank.gl.domain.exception.GlErrorCode;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class GlVoucherPersistenceAdapter implements VoucherSavePort {

    private static final String REFERENCE_KEY_UNIQUE_KEY = "uk_gl_voucher_tx_type_reference_key";

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
        // flush 가 여기서 일어나므로 참조 키 중복도 호출자 커밋 시점이 아니라 이 호출에서 드러난다.
        try {
            voucherRepository.saveAndFlush(GlVoucherMapper.toVoucherEntity(voucher));
        } catch (DataIntegrityViolationException exception) {
            if (violatesReferenceKey(exception)) {
                throw new BusinessException(GlErrorCode.DUPLICATE_REFERENCE_KEY, exception);
            }
            throw exception;
        }
        journalEntryRepository.saveAll(GlVoucherMapper.toJournalEntryEntities(voucher));
    }

    // MySQL 은 위반한 키를 'gl_voucher.uk_...' 처럼 테이블명과 함께 알려주므로 포함 여부로 판정한다.
    // 전표번호 PK 위반은 채번 결함이라 다른 원인이다 — 그대로 던진다.
    private static boolean violatesReferenceKey(DataIntegrityViolationException exception) {
        if (!(exception.getCause() instanceof ConstraintViolationException violation)) {
            return false;
        }
        String constraintName = violation.getConstraintName();
        return constraintName != null && constraintName.contains(REFERENCE_KEY_UNIQUE_KEY);
    }
}
