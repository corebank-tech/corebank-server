package com.shinhan.corebank.paymentstop.application.service;

import com.shinhan.corebank.paymentstop.application.port.out.AccountStopPort;
import com.shinhan.corebank.paymentstop.application.port.out.LedgerFactPort;
import com.shinhan.corebank.paymentstop.domain.DisputedTransfer;
import com.shinhan.corebank.paymentstop.domain.Objection;
import com.shinhan.corebank.paymentstop.domain.PaymentStopCase;
import com.shinhan.corebank.paymentstop.domain.gate.GateFacts;
import com.shinhan.corebank.paymentstop.domain.gate.GateResult;
import com.shinhan.corebank.paymentstop.domain.gate.GateSettings;
import com.shinhan.corebank.paymentstop.domain.gate.LedgerFact;
import com.shinhan.corebank.paymentstop.domain.gate.ReleaseGate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** 사건과 이의제기로 게이트 입력을 모아 계산한다. 입력은 원장 사실과 접수 정보뿐이다(D9). */
@Component
@RequiredArgsConstructor
public class GateEvaluator {

    private final LedgerFactPort ledgerFactPort;
    private final AccountStopPort accountStopPort;
    private final GateSettings settings;

    public GateResult evaluate(PaymentStopCase paymentStopCase, Objection objection) {
        LocalDateTime firstDisputedAt = paymentStopCase.getDisputedTransfers().stream()
                .map(DisputedTransfer::occurredAt)
                .min(Comparator.naturalOrder())
                .orElseThrow();
        LocalDateTime lastDisputedAt = paymentStopCase.getDisputedTransfers().stream()
                .map(DisputedTransfer::occurredAt)
                .max(Comparator.naturalOrder())
                .orElseThrow();

        // 관계 인정 조회 기간과 사기 패턴 창(전후 시간, 입금 뒤 출금 시간)을 모두 덮는다
        LocalDateTime from =
                firstDisputedAt.minusDays(settings.relationLookbackDays()).minusHours(settings.fraudWindowHours());
        LocalDateTime to =
                lastDisputedAt.plusHours(settings.fraudWindowHours()).plusMinutes(settings.fraudOutflowMinutes() + 1L);

        List<LedgerFact> accountTransfers = ledgerFactPort.findByAccount(paymentStopCase.getAccountId(), from, to);
        Map<String, LedgerFact> byNumber = accountTransfers.stream()
                .collect(Collectors.toMap(LedgerFact::transactionNumber, Function.identity(), (a, b) -> a));

        // 다툼 거래는 등록 때 원장에서 확인했다. 조회 창 밖으로 밀리는 일은 없지만, 없으면 등록 시점 값으로 채운다
        List<LedgerFact> disputed = paymentStopCase.getDisputedTransfers().stream()
                .map(transfer ->
                        byNumber.getOrDefault(transfer.transactionNumber(), snapshot(transfer, paymentStopCase)))
                .toList();

        boolean otherStopHistory =
                accountStopPort.hasOtherStopHistory(paymentStopCase.getAccountId(), paymentStopCase.getCaseId());

        GateFacts facts = new GateFacts(
                disputed,
                accountTransfers,
                paymentStopCase.getSenderAccountId(),
                otherStopHistory,
                objection.isSimplifiedRequested(),
                objection.getClause(),
                objection.isLitigationPending());

        return ReleaseGate.evaluate(facts, settings);
    }

    public GateSettings settings() {
        return settings;
    }

    private static LedgerFact snapshot(DisputedTransfer transfer, PaymentStopCase paymentStopCase) {
        Long sender = paymentStopCase.getSenderAccountId();
        return new LedgerFact(
                transfer.transactionNumber(), true, sender == null, sender, transfer.amount(), transfer.occurredAt());
    }
}
