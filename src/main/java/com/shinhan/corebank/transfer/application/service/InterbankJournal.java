package com.shinhan.corebank.transfer.application.service;

import com.shinhan.corebank.gl.api.GlTxType;
import com.shinhan.corebank.gl.api.JournalDirection;
import com.shinhan.corebank.gl.api.JournalLine;
import com.shinhan.corebank.gl.api.JournalRequest;
import java.time.LocalDate;
import java.util.List;

// 타행 미결제 2패턴(gl_journal_patterns.md §3-5). 송신 때 쌓은 미결제타점권을 확정 때 결제 또는 되돌림으로 비운다.
final class InterbankJournal {

    private static final String CASH = "10100";
    private static final String UNSETTLED = "10200";
    private static final String DEPOSIT = "20100";

    private InterbankJournal() {}

    static JournalRequest requested(String transactionNumber, LocalDate tradeDate, long amount) {
        return journal(GlTxType.INTERBANK_TRANSFER, transactionNumber, tradeDate, DEPOSIT, UNSETTLED, amount);
    }

    static JournalRequest approved(String transactionNumber, LocalDate tradeDate, long amount) {
        return journal(GlTxType.INTERBANK_SETTLEMENT, transactionNumber, tradeDate, UNSETTLED, CASH, amount);
    }

    static JournalRequest refunded(String transactionNumber, LocalDate tradeDate, long amount) {
        return journal(GlTxType.INTERBANK_SETTLEMENT, transactionNumber, tradeDate, UNSETTLED, DEPOSIT, amount);
    }

    private static JournalRequest journal(
            GlTxType txType,
            String transactionNumber,
            LocalDate tradeDate,
            String debitAccount,
            String creditAccount,
            long amount) {
        return new JournalRequest(
                txType,
                transactionNumber,
                tradeDate,
                List.of(
                        new JournalLine(debitAccount, JournalDirection.DEBIT, amount),
                        new JournalLine(creditAccount, JournalDirection.CREDIT, amount)));
    }
}
