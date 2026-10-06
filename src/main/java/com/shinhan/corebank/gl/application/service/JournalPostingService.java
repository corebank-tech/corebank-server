package com.shinhan.corebank.gl.application.service;

import com.shinhan.corebank.gl.api.JournalLine;
import com.shinhan.corebank.gl.api.JournalPostingUseCase;
import com.shinhan.corebank.gl.api.JournalRequest;
import com.shinhan.corebank.gl.application.port.out.VoucherSavePort;
import com.shinhan.corebank.gl.application.port.out.VoucherSequencePort;
import com.shinhan.corebank.gl.domain.JournalEntry;
import com.shinhan.corebank.gl.domain.Voucher;
import com.shinhan.corebank.gl.domain.VoucherNumber;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 전표 기표 (PH-24). 채번 → 차대변 검증 → 전표·분개 저장.
 *
 * <p>{@code MANDATORY} 라 원장을 기표한 트랜잭션 안에서만 돈다. 여기서 던진 예외는 그 트랜잭션을 롤백해 원장까지 되돌린다.
 * 채번만은 별도 트랜잭션으로 커밋되므로 롤백되면 결번이 남는다(gl_journal_patterns.md §1).
 */
@Service
@RequiredArgsConstructor
public class JournalPostingService implements JournalPostingUseCase {

    private final VoucherSequencePort voucherSequencePort;
    private final VoucherSavePort voucherSavePort;

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void post(JournalRequest request) {
        List<JournalEntry> entries =
                request.lines().stream().map(JournalPostingService::toEntry).toList();
        VoucherNumber number = voucherSequencePort.nextVoucherNumber(request.tradeDate(), request.txType());
        voucherSavePort.save(Voucher.create(number, request.referenceKey(), null, entries));
    }

    private static JournalEntry toEntry(JournalLine line) {
        return new JournalEntry(line.accountCode(), line.drCr(), line.amount());
    }
}
