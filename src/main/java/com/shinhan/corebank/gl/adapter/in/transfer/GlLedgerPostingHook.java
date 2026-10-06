package com.shinhan.corebank.gl.adapter.in.transfer;

import com.shinhan.corebank.common.exception.BusinessException;
import com.shinhan.corebank.gl.api.GlTxType;
import com.shinhan.corebank.gl.api.JournalDirection;
import com.shinhan.corebank.gl.api.JournalLine;
import com.shinhan.corebank.gl.api.JournalPostingUseCase;
import com.shinhan.corebank.gl.api.JournalRequest;
import com.shinhan.corebank.gl.domain.exception.GlErrorCode;
import com.shinhan.corebank.transfer.api.LedgerPostingContext;
import com.shinhan.corebank.transfer.api.LedgerPostingHook;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 이체 확장점 훅 B 의 GL 구현 (PH-24). 원장 2행이 기표된 직후 같은 트랜잭션에서 전표 1건을 세운다.
 *
 * <p>패턴은 docs/phase2/gl_journal_patterns.md §3 이다. 지금 오는 거래는 전부 예수금 안의 이동이라
 * 차 {@code 20100} / 대 {@code 20100} 이고 전표 유형만 다르다. 모르는 거래유형은 예외로 던진다 — 패턴 없이
 * 조용히 넘기면 "원장은 있는데 전표가 없는" 거래가 생긴다.
 */
@Component
@RequiredArgsConstructor
public class GlLedgerPostingHook implements LedgerPostingHook {

    private static final String DEPOSIT_ACCOUNT = "20100";

    private final JournalPostingUseCase journalPostingUseCase;

    @Override
    public void afterLedger(LedgerPostingContext context) {
        journalPostingUseCase.post(new JournalRequest(
                toGlTxType(context.txType()),
                context.transactionNumber(),
                context.tradeDate(),
                List.of(
                        new JournalLine(DEPOSIT_ACCOUNT, JournalDirection.DEBIT, context.amount()),
                        new JournalLine(DEPOSIT_ACCOUNT, JournalDirection.CREDIT, context.amount()))));
    }

    // 원장 transaction_type 값이 들어온다(LedgerPostingContext).
    private static GlTxType toGlTxType(String ledgerTransactionType) {
        return switch (ledgerTransactionType) {
            case "IMMEDIATE_TRANSFER", "SCHEDULED_TRANSFER", "AUTO_TRANSFER" -> GlTxType.TRANSFER;
            case "PRODUCT_SUBSCRIPTION" -> GlTxType.PRODUCT_SUBSCRIPTION;
            case "REVERSAL" -> GlTxType.REVERSAL;
            case null, default -> throw new BusinessException(GlErrorCode.UNSUPPORTED_LEDGER_TX_TYPE);
        };
    }
}
