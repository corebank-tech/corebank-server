package com.shinhan.corebank.paymentstop.application.service;

import com.shinhan.corebank.common.audit.AccessReason;
import com.shinhan.corebank.common.audit.AuditLogService;
import com.shinhan.corebank.common.audit.PersonalDataAccessEventType;
import com.shinhan.corebank.common.exception.BusinessException;
import com.shinhan.corebank.paymentstop.application.port.in.AdminActor;
import com.shinhan.corebank.paymentstop.application.port.in.ObjectionQueryUseCase;
import com.shinhan.corebank.paymentstop.application.port.out.PaymentStopRecordPort;
import com.shinhan.corebank.paymentstop.domain.Objection;
import com.shinhan.corebank.paymentstop.domain.PaymentStopCase;
import com.shinhan.corebank.paymentstop.domain.exception.PaymentStopErrorCode;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ObjectionQueryService implements ObjectionQueryUseCase {

    // 목록은 최근 순으로 이만큼만. 페이지와 검색 조건은 관리자 화면(PA-07)과 함께 정한다
    private static final int LIST_LIMIT = 200;

    private final PaymentStopRecordPort recordPort;
    private final GateEvaluator gateEvaluator;
    private final AuditLogService auditLogService;

    @Override
    @Transactional(readOnly = true)
    public List<Summary> list() {
        List<Objection> objections = recordPort.findRecentObjections(LIST_LIMIT);
        Map<Long, PaymentStopCase> cases = recordPort.findCases(
                objections.stream().map(Objection::getCaseId).collect(Collectors.toSet()));
        return objections.stream()
                .map(objection -> new Summary(objection, cases.get(objection.getCaseId())))
                .toList();
    }

    // 조회 감사 행을 같은 트랜잭션에 쓰므로 읽기 전용으로 두지 않는다
    @Override
    @Transactional
    public Detail detail(long objectionId, AdminActor actor, AccessReason accessReason) {
        Objection objection = recordPort
                .findObjection(objectionId)
                .orElseThrow(() -> new BusinessException(PaymentStopErrorCode.OBJECTION_NOT_FOUND));
        PaymentStopCase paymentStopCase = caseOf(objection);

        auditLogService.recordPersonalDataAccess(
                actor.adminCustomerId(),
                PersonalDataAccessEventType.OBJECTION_DETAIL_VIEW,
                actor.requestIp(),
                true,
                Map.of("objectionId", objectionId, "caseId", paymentStopCase.getCaseId()),
                accessReason);

        return new Detail(
                objection,
                paymentStopCase,
                gateEvaluator.evaluate(paymentStopCase, objection),
                recordPort.findDecision(objectionId));
    }

    private PaymentStopCase caseOf(Objection objection) {
        return recordPort
                .findCase(objection.getCaseId())
                .orElseThrow(() -> new IllegalStateException("이의제기의 사건이 없습니다. caseId=" + objection.getCaseId()));
    }
}
