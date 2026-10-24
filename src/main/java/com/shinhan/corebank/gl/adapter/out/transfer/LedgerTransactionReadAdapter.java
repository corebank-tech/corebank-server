package com.shinhan.corebank.gl.adapter.out.transfer;

import com.shinhan.corebank.gl.application.port.out.LedgerTransactionReadPort;
import com.shinhan.corebank.gl.domain.LedgerTransaction;
import com.shinhan.corebank.transfer.api.LedgerTransactionQuery;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

// 원장은 transfer 소유다. transfer.api 계약으로만 읽는다(ADR-0006).
@Component
@RequiredArgsConstructor
public class LedgerTransactionReadAdapter implements LedgerTransactionReadPort {

    private final LedgerTransactionQuery ledgerTransactionQuery;

    @Override
    public List<LedgerTransaction> findByTradeDate(LocalDate tradeDate, String afterTransactionNumber, int limit) {
        return ledgerTransactionQuery.findByTradeDate(tradeDate, afterTransactionNumber, limit).stream()
                .map(summary ->
                        new LedgerTransaction(summary.transactionNumber(), summary.transactionType(), summary.amount()))
                .toList();
    }
}
