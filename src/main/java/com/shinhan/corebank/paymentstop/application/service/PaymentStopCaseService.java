package com.shinhan.corebank.paymentstop.application.service;

import com.shinhan.corebank.common.audit.AuditEventType;
import com.shinhan.corebank.common.audit.AuditLogService;
import com.shinhan.corebank.common.exception.BusinessException;
import com.shinhan.corebank.common.exception.CommonErrorCode;
import com.shinhan.corebank.paymentstop.application.port.in.AdminActor;
import com.shinhan.corebank.paymentstop.application.port.in.RegisterPaymentStopCaseUseCase;
import com.shinhan.corebank.paymentstop.application.port.out.AccountStopPort;
import com.shinhan.corebank.paymentstop.application.port.out.LedgerFactPort;
import com.shinhan.corebank.paymentstop.application.port.out.PaymentStopRecordPort;
import com.shinhan.corebank.paymentstop.domain.DisputedTransfer;
import com.shinhan.corebank.paymentstop.domain.PaymentStopCase;
import com.shinhan.corebank.paymentstop.domain.exception.PaymentStopErrorCode;
import com.shinhan.corebank.paymentstop.domain.gate.LedgerFact;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PaymentStopCaseService implements RegisterPaymentStopCaseUseCase {

    // 다툼 거래를 찾는 기간. 피해 신고는 거래 직후에 들어오므로 넉넉히 잡은 내부 기준이다
    private static final int DISPUTED_LOOKBACK_DAYS = 180;

    private final PaymentStopRecordPort recordPort;
    private final LedgerFactPort ledgerFactPort;
    private final AccountStopPort accountStopPort;
    private final AuditLogService auditLogService;
    private final Clock clock;

    @Override
    @Transactional
    public long register(Command command, AdminActor actor) {
        List<String> numbers = command.disputedTransactionNumbers();
        if (numbers == null || numbers.isEmpty() || new HashSet<>(numbers).size() != numbers.size()) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT);
        }

        LocalDateTime now = LocalDateTime.now(clock);
        Map<String, LedgerFact> transfers =
                ledgerFactPort
                        .findByAccount(command.accountId(), now.minusDays(DISPUTED_LOOKBACK_DAYS), now.plusSeconds(1))
                        .stream()
                        .collect(Collectors.toMap(LedgerFact::transactionNumber, Function.identity(), (a, b) -> a));

        List<LedgerFact> disputed = numbers.stream()
                .map(number -> {
                    LedgerFact fact = transfers.get(number);
                    if (fact == null || !fact.incoming()) {
                        throw new BusinessException(PaymentStopErrorCode.DISPUTED_TRANSFER_NOT_DEPOSIT);
                    }
                    return fact;
                })
                .toList();

        // 송금인이 한 명의 당행 계좌일 때만 관계 인정 대상이다
        List<Long> senders = disputed.stream()
                .map(LedgerFact::counterpartyAccountId)
                .distinct()
                .toList();
        Long senderAccountId = senders.size() == 1 && senders.getFirst() != null ? senders.getFirst() : null;

        PaymentStopCase saved = recordPort.saveNewCase(PaymentStopCase.register(
                command.accountId(),
                senderAccountId,
                command.objectionDeadline(),
                actor.adminCustomerId(),
                disputed.stream()
                        .map(fact -> new DisputedTransfer(fact.transactionNumber(), fact.amount(), fact.occurredAt()))
                        .toList()));

        accountStopPort.applyFullStop(command.accountId(), Objects.requireNonNull(saved.getCaseId()));

        auditLogService.record(
                actor.adminCustomerId(),
                null,
                AuditEventType.PAYMENT_STOP_APPLY,
                actor.requestIp(),
                true,
                Map.of(
                        "caseId", saved.getCaseId(),
                        "accountId", command.accountId(),
                        "disputedCount", disputed.size(),
                        "disputedTotal", saved.disputedTotal()));

        return saved.getCaseId();
    }
}
