package com.shinhan.corebank.paymentstop.application.service;

import com.shinhan.corebank.common.exception.BusinessException;
import com.shinhan.corebank.common.util.MaskingUtil;
import com.shinhan.corebank.paymentstop.application.port.in.CustomerObjectionUseCase;
import com.shinhan.corebank.paymentstop.application.port.out.CustomerAccountPort;
import com.shinhan.corebank.paymentstop.application.port.out.ObjectionReviewPort;
import com.shinhan.corebank.paymentstop.application.port.out.PaymentStopRecordPort;
import com.shinhan.corebank.paymentstop.domain.Objection;
import com.shinhan.corebank.paymentstop.domain.ObjectionChannel;
import com.shinhan.corebank.paymentstop.domain.ObjectionDecision;
import com.shinhan.corebank.paymentstop.domain.ObjectionStatus;
import com.shinhan.corebank.paymentstop.domain.PaymentStopCase;
import com.shinhan.corebank.paymentstop.domain.SupplementRequest;
import com.shinhan.corebank.paymentstop.domain.document.DocumentKind;
import com.shinhan.corebank.paymentstop.domain.document.ObjectionDocument;
import com.shinhan.corebank.paymentstop.domain.document.UploadFile;
import com.shinhan.corebank.paymentstop.domain.document.UploaderRole;
import com.shinhan.corebank.paymentstop.domain.exception.PaymentStopErrorCode;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 고객 비대면 이의제기(D34). 본인 계좌 사건인지는 account.api 로 고객 명의 계좌를 읽어 확인한다. 남의 사건, 없는 사건, 해제된 사건은 모두
 * 같은 오류(사건은 PST0201, 이의제기는 PST0202)로 거부한다. 두 경로 모두 계좌 조회와 사건 조회를 같은 순서로 한 번씩 해 연산량도 같다(AGENTS
 * 규칙 4).
 */
@Service
@RequiredArgsConstructor
public class CustomerObjectionService implements CustomerObjectionUseCase {

    private final PaymentStopRecordPort recordPort;
    private final ObjectionReviewPort reviewPort;
    private final CustomerAccountPort customerAccountPort;
    private final ObjectionService objectionService;
    private final DocumentStore documentStore;
    private final ReviewJobEnqueuer reviewJobEnqueuer;
    private final Clock clock;

    @Override
    @Transactional(readOnly = true)
    public List<MyCase> myCases(long customerId) {
        Map<Long, String> accounts = customerAccountPort.accountNumbersOf(customerId);
        List<PaymentStopCase> cases = recordPort.findCasesByAccountIds(accounts.keySet());
        Map<Long, Objection> objections = recordPort.findObjectionsByCaseIds(
                cases.stream().map(PaymentStopCase::getCaseId).toList());
        List<Long> objectionIds =
                objections.values().stream().map(Objection::getObjectionId).toList();
        Map<Long, SupplementRequest> supplements = reviewPort.findLatestSupplementRequests(objectionIds);
        Map<Long, ObjectionDecision> decisions = recordPort.findConfirmedDecisions(objectionIds);

        return cases.stream()
                .map(paymentStopCase -> {
                    Objection objection = objections.get(paymentStopCase.getCaseId());
                    Long objectionId = objection == null ? null : objection.getObjectionId();
                    SupplementRequest supplement = objectionId == null ? null : supplements.get(objectionId);
                    ObjectionDecision decision = objectionId == null ? null : decisions.get(objectionId);
                    boolean awaitingSupplement =
                            objection != null && objection.getStatus() == ObjectionStatus.SUPPLEMENT_REQUESTED;
                    return new MyCase(
                            paymentStopCase.getCaseId(),
                            MaskingUtil.maskAccountNumber(accounts.get(paymentStopCase.getAccountId())),
                            paymentStopCase.getStatus().name(),
                            paymentStopCase.disputedTotal(),
                            paymentStopCase.getObjectionDeadline(),
                            objectionId,
                            objection == null ? null : objection.getStatus(),
                            awaitingSupplement ? objection.getSupplementDue() : null,
                            awaitingSupplement && supplement != null ? supplement.missingDocuments() : List.of(),
                            decision == null ? null : decision.result());
                })
                .toList();
    }

    @Override
    @Transactional
    public long submit(long customerId, SubmitCommand command) {
        PaymentStopCase paymentStopCase = ownedCase(customerId, command.caseId());

        if (command.idCopy() == null) {
            throw new BusinessException(PaymentStopErrorCode.OBJECTION_REQUIREMENTS_MISSING);
        }
        List<UploadFile> evidence = command.evidence() == null ? List.of() : command.evidence();
        List<UploadFile> all = new ArrayList<>();
        all.add(command.idCopy());
        all.addAll(evidence);
        // 행을 넣기 전에 파일을 모두 검사해 접수만 남고 파일이 빠지는 일이 없게 한다
        documentStore.validate(null, all);

        // 접수, 분석 대기열, 접수 통지(D30, D35)
        long objectionId = objectionService.receive(
                paymentStopCase,
                command.clause(),
                command.statement(),
                command.simplifiedRequested(),
                true,
                command.litigationPending(),
                customerId,
                ObjectionChannel.ONLINE);

        LocalDateTime now = LocalDateTime.now(clock);
        documentStore.store(
                objectionId, DocumentKind.ID_COPY, List.of(command.idCopy()), 0, customerId, UploaderRole.HOLDER, now);
        if (!evidence.isEmpty()) {
            documentStore.store(objectionId, DocumentKind.EVIDENCE, evidence, 0, customerId, UploaderRole.HOLDER, now);
        }
        return objectionId;
    }

    @Override
    @Transactional
    public List<ObjectionDocument> submitSupplement(
            long customerId, long objectionId, List<UploadFile> evidence, String note) {
        Map<Long, String> accounts = customerAccountPort.accountNumbersOf(customerId);
        boolean owned = recordPort
                .findObjection(objectionId)
                .flatMap(found -> recordPort.findCase(found.getCaseId()))
                .filter(paymentStopCase -> accounts.containsKey(paymentStopCase.getAccountId()))
                .isPresent();
        if (!owned) {
            throw new BusinessException(PaymentStopErrorCode.OBJECTION_NOT_FOUND);
        }
        // 동시 제출이 함께 상태를 바꾸지 않게 이의제기 행을 잠그고 상태를 다시 읽는다
        Objection objection = recordPort.findObjectionForUpdate(objectionId).orElseThrow();

        objection.submitSupplement();
        documentStore.validate(objectionId, evidence);

        LocalDateTime now = LocalDateTime.now(clock);
        List<ObjectionDocument> saved = documentStore.store(
                objectionId,
                DocumentKind.EVIDENCE,
                evidence,
                objection.getSupplementRound(),
                customerId,
                UploaderRole.HOLDER,
                now);
        reviewPort.markSupplementSubmitted(
                objectionId, objection.getSupplementRound(), now, note == null || note.isBlank() ? null : note.strip());
        recordPort.updateObjection(objection);
        // 보완 자료를 다시 분석한다(D35). 소명 완비는 담당자가 다시 확인한다(D12)
        reviewJobEnqueuer.enqueue(objectionId, customerId);
        return saved;
    }

    private PaymentStopCase ownedCase(long customerId, long caseId) {
        Map<Long, String> accounts = customerAccountPort.accountNumbersOf(customerId);
        return recordPort
                .findCase(caseId)
                .filter(paymentStopCase -> accounts.containsKey(paymentStopCase.getAccountId()))
                .orElseThrow(() -> new BusinessException(PaymentStopErrorCode.CASE_NOT_FOUND));
    }
}
