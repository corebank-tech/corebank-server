package com.shinhan.corebank.paymentstop.adapter.out.persistence;

import com.shinhan.corebank.common.exception.BusinessException;
import com.shinhan.corebank.paymentstop.application.port.out.PaymentStopRecordPort;
import com.shinhan.corebank.paymentstop.domain.DecisionStatus;
import com.shinhan.corebank.paymentstop.domain.DisputedTransfer;
import com.shinhan.corebank.paymentstop.domain.Objection;
import com.shinhan.corebank.paymentstop.domain.ObjectionDecision;
import com.shinhan.corebank.paymentstop.domain.PaymentStopCase;
import com.shinhan.corebank.paymentstop.domain.exception.PaymentStopErrorCode;
import com.shinhan.corebank.paymentstop.domain.gate.GateResult;
import com.shinhan.corebank.paymentstop.domain.gate.GateSettings;
import com.shinhan.corebank.paymentstop.domain.gate.RuleResult;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PaymentStopRecordPersistenceAdapter implements PaymentStopRecordPort {

    private final PaymentStopCaseJpaRepository caseRepository;
    private final CaseDisputedTransferJpaRepository disputedTransferRepository;
    private final ObjectionJpaRepository objectionRepository;
    private final ObjectionDecisionJpaRepository decisionRepository;
    private final GateEvaluationJpaRepository gateEvaluationRepository;

    @Override
    public PaymentStopCase saveNewCase(PaymentStopCase paymentStopCase) {
        PaymentStopCaseJpaEntity saved = caseRepository.save(PaymentStopCaseJpaEntity.of(
                paymentStopCase.getAccountId(),
                paymentStopCase.getSenderAccountId(),
                paymentStopCase.getObjectionDeadline(),
                paymentStopCase.getStatus(),
                paymentStopCase.getRegisteredBy()));

        List<CaseDisputedTransferJpaEntity> disputed = paymentStopCase.getDisputedTransfers().stream()
                .map(transfer -> CaseDisputedTransferJpaEntity.of(
                        saved.getCaseId(), transfer.transactionNumber(), transfer.amount(), transfer.occurredAt()))
                .toList();
        try {
            // 같은 다툼 거래의 중복 등록은 uk_case_disputed_transfer_txno 가 막는다. 여기서 바로 드러나게 flush 한다
            disputedTransferRepository.saveAllAndFlush(disputed);
        } catch (DataIntegrityViolationException e) {
            throw uniqueViolation(
                    e, "uk_case_disputed_transfer_txno", PaymentStopErrorCode.DISPUTED_TRANSFER_ALREADY_REGISTERED);
        }

        return toCase(saved);
    }

    @Override
    public Optional<PaymentStopCase> findCase(long caseId) {
        return caseRepository.findById(caseId).map(this::toCase);
    }

    @Override
    public Map<Long, PaymentStopCase> findCases(Collection<Long> caseIds) {
        if (caseIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, List<DisputedTransfer>> disputedByCase =
                disputedTransferRepository.findByCaseIdInOrderByOccurredAtAscDisputedTransferIdAsc(caseIds).stream()
                        .collect(Collectors.groupingBy(
                                CaseDisputedTransferJpaEntity::getCaseId,
                                Collectors.mapping(
                                        PaymentStopRecordPersistenceAdapter::toDisputed, Collectors.toList())));
        return caseRepository.findAllById(caseIds).stream()
                .map(entity -> toCase(entity, disputedByCase.getOrDefault(entity.getCaseId(), List.of())))
                .collect(Collectors.toMap(PaymentStopCase::getCaseId, Function.identity()));
    }

    @Override
    public void updateCaseStatus(PaymentStopCase paymentStopCase) {
        caseRepository
                .findById(paymentStopCase.getCaseId())
                .orElseThrow(() -> new IllegalStateException("사건이 없습니다. caseId=" + paymentStopCase.getCaseId()))
                .changeStatus(paymentStopCase.getStatus());
    }

    @Override
    public List<PaymentStopCase> findCasesByAccountIds(Collection<Long> accountIds) {
        if (accountIds.isEmpty()) {
            return List.of();
        }
        List<Long> caseIds = caseRepository.findByAccountIdInOrderByCaseIdDesc(accountIds).stream()
                .map(PaymentStopCaseJpaEntity::getCaseId)
                .toList();
        Map<Long, PaymentStopCase> cases = findCases(caseIds);
        return caseIds.stream().map(cases::get).toList();
    }

    @Override
    public boolean existsObjectionByCaseId(long caseId) {
        return objectionRepository.existsByCaseId(caseId);
    }

    @Override
    public Objection saveNewObjection(Objection objection) {
        try {
            return toObjection(objectionRepository.saveAndFlush(ObjectionJpaEntity.of(
                    objection.getCaseId(),
                    objection.getClause(),
                    objection.getStatement(),
                    objection.isSimplifiedRequested(),
                    objection.isIdCopyConfirmed(),
                    objection.isLitigationPending(),
                    objection.getStatus(),
                    objection.getReceivedBy(),
                    objection.getReceivedChannel(),
                    objection.getReceivedAt())));
        } catch (DataIntegrityViolationException e) {
            throw uniqueViolation(e, "uk_objection_case", PaymentStopErrorCode.OBJECTION_ALREADY_RECEIVED);
        }
    }

    @Override
    public Optional<Objection> findObjection(long objectionId) {
        return objectionRepository.findById(objectionId).map(PaymentStopRecordPersistenceAdapter::toObjection);
    }

    @Override
    public Optional<Objection> findObjectionForUpdate(long objectionId) {
        return objectionRepository.findByIdForUpdate(objectionId).map(PaymentStopRecordPersistenceAdapter::toObjection);
    }

    @Override
    public void updateObjection(Objection objection) {
        objectionRepository
                .findById(objection.getObjectionId())
                .orElseThrow(() -> new IllegalStateException("이의제기가 없습니다. objectionId=" + objection.getObjectionId()))
                .apply(objection.getStatus(), objection.progress());
    }

    @Override
    public List<Objection> findRecentObjections(int limit) {
        return objectionRepository.findAllByOrderByObjectionIdDesc(PageRequest.of(0, limit)).stream()
                .map(PaymentStopRecordPersistenceAdapter::toObjection)
                .toList();
    }

    @Override
    public Map<Long, Objection> findObjectionsByCaseIds(Collection<Long> caseIds) {
        if (caseIds.isEmpty()) {
            return Map.of();
        }
        return objectionRepository.findByCaseIdIn(caseIds).stream()
                .map(PaymentStopRecordPersistenceAdapter::toObjection)
                .collect(Collectors.toMap(Objection::getCaseId, Function.identity()));
    }

    @Override
    public ObjectionDecision saveDecision(ObjectionDecision decision) {
        try {
            // 반려되지 않은 판정이 이미 있으면 uk_objection_decision_active 가 막는다. 호출자는 이의제기 행 락을 쥐고 있어 정상
            // 흐름에서는 걸리지 않는다
            return decisionRepository
                    .saveAndFlush(ObjectionDecisionJpaEntity.of(decision))
                    .toDomain();
        } catch (DataIntegrityViolationException e) {
            throw uniqueViolation(e, "uk_objection_decision_active", PaymentStopErrorCode.INVALID_OBJECTION_STATE);
        }
    }

    @Override
    public void updateDecision(ObjectionDecision decision) {
        decisionRepository
                .findById(decision.decisionId())
                .orElseThrow(() -> new IllegalStateException("판정이 없습니다. decisionId=" + decision.decisionId()))
                .apply(decision);
    }

    @Override
    public Optional<ObjectionDecision> findDecisionById(long decisionId) {
        return decisionRepository.findById(decisionId).map(ObjectionDecisionJpaEntity::toDomain);
    }

    @Override
    public Optional<ObjectionDecision> findConfirmedDecision(long objectionId) {
        return decisionRepository
                .findFirstByObjectionIdAndStatus(objectionId, DecisionStatus.CONFIRMED)
                .map(ObjectionDecisionJpaEntity::toDomain);
    }

    @Override
    public Optional<ObjectionDecision> findPendingDecision(long objectionId) {
        return decisionRepository
                .findFirstByObjectionIdAndStatus(objectionId, DecisionStatus.PENDING_APPROVAL)
                .map(ObjectionDecisionJpaEntity::toDomain);
    }

    @Override
    public Map<Long, ObjectionDecision> findConfirmedDecisions(Collection<Long> objectionIds) {
        if (objectionIds.isEmpty()) {
            return Map.of();
        }
        return decisionRepository.findByObjectionIdInAndStatus(objectionIds, DecisionStatus.CONFIRMED).stream()
                .map(ObjectionDecisionJpaEntity::toDomain)
                .collect(Collectors.toMap(ObjectionDecision::objectionId, Function.identity(), (a, b) -> a));
    }

    @Override
    public Set<Long> findDiscretionaryObjectionIds(Collection<Long> objectionIds) {
        if (objectionIds.isEmpty()) {
            return Set.of();
        }
        return Set.copyOf(decisionRepository.findDiscretionaryObjectionIds(objectionIds, DecisionStatus.RETURNED));
    }

    @Override
    public void saveGateEvaluation(
            long objectionId, String purpose, GateResult result, GateSettings settings, LocalDateTime evaluatedAt) {
        gateEvaluationRepository.save(GateEvaluationJpaEntity.of(
                objectionId,
                purpose,
                result.branch().name(),
                result.simplifiedExcluded(),
                result.rulesVersion(),
                settingsSnapshot(settings),
                result.rules().stream()
                        .map(PaymentStopRecordPersistenceAdapter::ruleSnapshot)
                        .toList(),
                evaluatedAt));
    }

    private PaymentStopCase toCase(PaymentStopCaseJpaEntity entity) {
        return toCase(
                entity,
                disputedTransferRepository
                        .findByCaseIdOrderByOccurredAtAscDisputedTransferIdAsc(entity.getCaseId())
                        .stream()
                        .map(PaymentStopRecordPersistenceAdapter::toDisputed)
                        .toList());
    }

    private static PaymentStopCase toCase(PaymentStopCaseJpaEntity entity, List<DisputedTransfer> disputed) {
        return PaymentStopCase.reconstitute(
                entity.getCaseId(),
                entity.getAccountId(),
                entity.getSenderAccountId(),
                entity.getObjectionDeadline(),
                entity.getStatus(),
                entity.getRegisteredBy(),
                disputed);
    }

    private static DisputedTransfer toDisputed(CaseDisputedTransferJpaEntity row) {
        return new DisputedTransfer(row.getTransactionNumber(), row.getAmount(), row.getOccurredAt());
    }

    private static Objection toObjection(ObjectionJpaEntity entity) {
        return Objection.reconstitute(
                entity.getObjectionId(),
                entity.getCaseId(),
                entity.getClause(),
                entity.getStatement(),
                entity.isSimplifiedRequested(),
                entity.isIdCopyConfirmed(),
                entity.isLitigationPending(),
                entity.getStatus(),
                entity.getReceivedBy(),
                entity.getReceivedChannel(),
                entity.getReceivedAt(),
                entity.progress());
    }

    private static Map<String, Object> settingsSnapshot(GateSettings settings) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("fraudWindowHours", settings.fraudWindowHours());
        snapshot.put("fraudMinSenders", settings.fraudMinSenders());
        snapshot.put("fraudMinDeposit", settings.fraudMinDeposit());
        snapshot.put("fraudOutflowPercent", settings.fraudOutflowPercent());
        snapshot.put("fraudOutflowMinutes", settings.fraudOutflowMinutes());
        snapshot.put("relationLookbackDays", settings.relationLookbackDays());
        snapshot.put("relationMinCount", settings.relationMinCount());
        snapshot.put("relationMinSpanDays", settings.relationMinSpanDays());
        snapshot.put("relationMinLeadDays", settings.relationMinLeadDays());
        snapshot.put("relationMaxAmountMultiple", settings.relationMaxAmountMultiple());
        snapshot.put("partialSmallAmount", settings.partialSmallAmount());
        snapshot.put("partialLivelihoodDays", settings.partialLivelihoodDays());
        snapshot.put("partialLivelihoodMonths", settings.partialLivelihoodMonths());
        return snapshot;
    }

    private static Map<String, Object> ruleSnapshot(RuleResult rule) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("rule", rule.rule().name());
        snapshot.put("basis", rule.rule().basis().name());
        snapshot.put("matched", rule.matched());
        snapshot.put("transactionNumbers", rule.usedTransactionNumbers());
        snapshot.put("detail", rule.detail());
        return snapshot;
    }

    private static RuntimeException uniqueViolation(
            DataIntegrityViolationException e, String constraint, PaymentStopErrorCode errorCode) {
        String message = e.getMostSpecificCause().getMessage();
        if (message != null && message.contains(constraint)) {
            return new BusinessException(errorCode);
        }
        return e;
    }
}
