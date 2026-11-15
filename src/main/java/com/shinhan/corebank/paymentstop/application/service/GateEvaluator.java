package com.shinhan.corebank.paymentstop.application.service;

import com.shinhan.corebank.paymentstop.application.port.out.AccountStopPort;
import com.shinhan.corebank.paymentstop.application.port.out.LedgerFactPort;
import com.shinhan.corebank.paymentstop.application.port.out.ObjectionReviewPort;
import com.shinhan.corebank.paymentstop.domain.DisputedTransfer;
import com.shinhan.corebank.paymentstop.domain.Objection;
import com.shinhan.corebank.paymentstop.domain.PaymentStopCase;
import com.shinhan.corebank.paymentstop.domain.external.ExternalCheck;
import com.shinhan.corebank.paymentstop.domain.gate.ExternalReply;
import com.shinhan.corebank.paymentstop.domain.gate.GateFacts;
import com.shinhan.corebank.paymentstop.domain.gate.GateResult;
import com.shinhan.corebank.paymentstop.domain.gate.GateSettings;
import com.shinhan.corebank.paymentstop.domain.gate.LedgerFact;
import com.shinhan.corebank.paymentstop.domain.gate.ReleaseGate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 사건과 이의제기로 게이트 입력을 모아 계산한다. 입력은 원장 사실, 외부 확인 회신, 접수 정보뿐이다(D9).
 *
 * <p>두 단계로 나눌 수 있다. {@link #prepare}는 락 밖에서 원장 사실을, {@link #finish}는 락 안에서 바뀔 수 있는 입력을 읽는다.
 */
@Component
@RequiredArgsConstructor
public class GateEvaluator {

    private final LedgerFactPort ledgerFactPort;
    private final AccountStopPort accountStopPort;
    private final ObjectionReviewPort reviewPort;
    private final GateSettings settings;

    public GateResult evaluate(PaymentStopCase paymentStopCase, Objection objection) {
        return finish(prepare(paymentStopCase), objection);
    }

    /**
     * 락 밖에서 미리 계산해 둘 수 있는 원장 사실. 전부 지급정지 중에는 입출금이 막혀 원장이 바뀌지 않으므로, 180일 원장 조회를 락을 쥐지
     * 않고 한다.
     */
    public LedgerInputs prepare(PaymentStopCase paymentStopCase) {
        Window window = window(paymentStopCase);

        List<LedgerFact> accountTransfers =
                ledgerFactPort.findByAccount(paymentStopCase.getAccountId(), window.from(), window.to());
        Map<String, LedgerFact> byNumber = accountTransfers.stream()
                .collect(Collectors.toMap(LedgerFact::transactionNumber, Function.identity(), (a, b) -> a));

        // 다툼 거래는 등록 때 원장에서 확인했다. 조회 창 밖으로 밀리는 일은 없지만, 없으면 등록 시점 값으로 채운다
        List<LedgerFact> disputed = paymentStopCase.getDisputedTransfers().stream()
                .map(transfer ->
                        byNumber.getOrDefault(transfer.transactionNumber(), snapshot(transfer, paymentStopCase)))
                .toList();

        return new LedgerInputs(
                paymentStopCase.getAccountId(),
                paymentStopCase.getCaseId(),
                paymentStopCase.getSenderAccountId(),
                disputed,
                accountTransfers);
    }

    /**
     * 바뀔 수 있는 입력(외부 확인 회신, 다른 지급정지 이력, 접수 정보)을 지금 읽어 최종 결과를 만든다. 확정 직전에는 이의제기 행 락을 잡은
     * 뒤 부른다. 외부 확인 회신은 같은 락 안에서 저장되므로, 락을 잡은 뒤 읽으면 먼저 커밋된 회신을 놓치지 않는다(PR #919 리뷰 R1).
     */
    public GateResult finish(LedgerInputs ledger, Objection objection) {
        boolean otherStopHistory = accountStopPort.hasOtherStopHistory(ledger.accountId(), ledger.caseId());

        // 회신이 온 외부 확인만 넣는다. 해당 기록 없음(NO_RECORD)은 게이트에 영향이 없다
        List<ExternalReply> replies = reviewPort.findExternalChecks(objection.getObjectionId()).stream()
                .map(ExternalCheck::gateReply)
                .flatMap(Optional::stream)
                .toList();

        GateFacts facts = new GateFacts(
                ledger.disputed(),
                ledger.accountTransfers(),
                ledger.senderAccountId(),
                otherStopHistory,
                objection.isSimplifiedRequested(),
                objection.getClause(),
                objection.isLitigationPending(),
                replies);

        return ReleaseGate.evaluate(facts, settings);
    }

    public GateSettings settings() {
        return settings;
    }

    /**
     * 게이트가 읽는 원장 기간. 관계 인정 조회 기간과 사기 패턴 창(전후 시간, 입금 뒤 출금 시간)을 모두 덮는다. Agent 의 원장 조회와 판정안
     * 근거 검증도 이 기간 안으로 묶는다.
     */
    public Window window(PaymentStopCase paymentStopCase) {
        LocalDateTime firstDisputedAt = paymentStopCase.getDisputedTransfers().stream()
                .map(DisputedTransfer::occurredAt)
                .min(Comparator.naturalOrder())
                .orElseThrow();
        LocalDateTime lastDisputedAt = paymentStopCase.getDisputedTransfers().stream()
                .map(DisputedTransfer::occurredAt)
                .max(Comparator.naturalOrder())
                .orElseThrow();
        return new Window(
                firstDisputedAt,
                firstDisputedAt
                        .minusDays(Math.max(settings.relationLookbackDays(), settings.partialLivelihoodDays()))
                        .minusHours(settings.fraudWindowHours()),
                lastDisputedAt.plusHours(settings.fraudWindowHours()).plusMinutes(settings.fraudOutflowMinutes() + 1L));
    }

    /** @param to 미포함 */
    public record Window(LocalDateTime firstDisputedAt, LocalDateTime from, LocalDateTime to) {}

    /** 락 밖에서 계산한 원장 사실. {@link #finish}가 락 안에서 바뀔 수 있는 입력과 합친다. */
    public record LedgerInputs(
            long accountId,
            long caseId,
            Long senderAccountId,
            List<LedgerFact> disputed,
            List<LedgerFact> accountTransfers) {

        public LedgerInputs {
            disputed = List.copyOf(disputed);
            accountTransfers = List.copyOf(accountTransfers);
        }
    }

    private static LedgerFact snapshot(DisputedTransfer transfer, PaymentStopCase paymentStopCase) {
        Long sender = paymentStopCase.getSenderAccountId();
        return new LedgerFact(
                transfer.transactionNumber(), true, sender == null, sender, transfer.amount(), transfer.occurredAt());
    }
}
