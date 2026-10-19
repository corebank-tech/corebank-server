package com.shinhan.corebank.transfer.domain;

import com.shinhan.corebank.common.exception.CommonErrorCode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class LedgerEntry {

    private final Long ledgerEntryId;
    private final LocalDateTime occurredAt;
    private final Long accountId;
    private final Long transferId;
    private final String transactionNumber;
    private final LedgerDirection direction;
    private final long amount;
    private final long balanceAfter;
    private final String transactionType;
    private final String transactionContent;
    private final TransferChannel channel;
    private final boolean reversed;
    private final Long reversalId;
    // 거래일(귀속 영업일). occurredAt(발생 시각)과 다르다 — 휴일 거래는 다음 영업일이다.
    private final LocalDate tradeDate;

    /**
     * 상대 고객 계좌가 없는 입금 1행(이자 지급 등). 상대편은 호출자가 올리는 GL 전표라 {@link LedgerPair}로
     * 만들지 않는다 — 2행 경로와 타입이 섞이지 않게 1행은 이 팩토리로만 만든다.
     */
    public static LedgerEntry forCredit(
            String transactionNumber,
            Long accountId,
            long balanceAfter,
            long amount,
            String transactionType,
            String passbookMemo,
            TransferChannel channel,
            LocalDateTime occurredAt,
            LocalDate tradeDate) {
        TransferValidations.requireNonNull(accountId, CommonErrorCode.REQUIRED_FIELD_MISSING);
        TransferValidations.requireNonNull(occurredAt, CommonErrorCode.REQUIRED_FIELD_MISSING);
        TransferValidations.requireNonNull(tradeDate, CommonErrorCode.REQUIRED_FIELD_MISSING);
        TransferValidations.requireNonBlank(transactionNumber, CommonErrorCode.REQUIRED_FIELD_MISSING);
        TransferValidations.requireNonBlank(transactionType, CommonErrorCode.REQUIRED_FIELD_MISSING);
        TransferValidations.requireNonNull(channel, CommonErrorCode.REQUIRED_FIELD_MISSING);
        TransferValidations.requirePositiveAmount(amount);

        return LedgerEntry.builder()
                .accountId(accountId)
                .transactionNumber(transactionNumber)
                .direction(LedgerDirection.DEPOSIT)
                .amount(amount)
                .balanceAfter(balanceAfter)
                .transactionType(transactionType)
                .transactionContent(passbookMemo)
                .channel(channel)
                .reversed(false)
                .occurredAt(occurredAt.truncatedTo(ChronoUnit.MICROS))
                .tradeDate(tradeDate)
                .build();
    }
}
