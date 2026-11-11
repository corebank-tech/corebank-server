package com.shinhan.corebank.paymentstop.application.port.out;

import com.shinhan.corebank.paymentstop.domain.Objection;
import com.shinhan.corebank.paymentstop.domain.ObjectionDecision;
import com.shinhan.corebank.paymentstop.domain.PaymentStopCase;
import com.shinhan.corebank.paymentstop.domain.gate.GateResult;
import com.shinhan.corebank.paymentstop.domain.gate.GateSettings;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

// paymentstop 도메인의 사건, 이의제기, 판정, 게이트 기록 저장소.
public interface PaymentStopRecordPort {

    /** 사건과 다툼 거래를 저장한다. 다른 사건에 이미 있는 다툼 거래면 PST0303. */
    PaymentStopCase saveNewCase(PaymentStopCase paymentStopCase);

    Optional<PaymentStopCase> findCase(long caseId);

    /** 사건의 계좌 ID. 본인 확인 전용이라 다툼 거래를 읽지 않는다. */
    Optional<Long> findCaseAccountId(long caseId);

    /** 이의제기가 속한 사건의 계좌 ID. 이의제기와 사건을 한 번에 읽는다. 본인 확인 전용이다. */
    Optional<Long> findObjectionAccountId(long objectionId);

    // 목록용. 사건과 다툼 거래를 사건 수와 무관하게 두 번의 조회로 읽는다
    Map<Long, PaymentStopCase> findCases(Collection<Long> caseIds);

    void updateCaseStatus(PaymentStopCase paymentStopCase);

    // 고객 화면용. 그 고객의 계좌에 걸린 사건 전부
    List<PaymentStopCase> findCasesByAccountIds(Collection<Long> accountIds);

    boolean existsObjectionByCaseId(long caseId);

    /** 사건당 하나뿐이라 동시 접수는 유니크 제약이 막는다. 걸리면 PST0304. */
    Objection saveNewObjection(Objection objection);

    Optional<Objection> findObjection(long objectionId);

    // 판정 확정 중복을 막으려고 이의제기 행을 잠근다. 반드시 트랜잭션 안에서 부른다
    Optional<Objection> findObjectionForUpdate(long objectionId);

    /** 상태와 완비 확인, 기한, 보완 진행을 저장한다. */
    void updateObjection(Objection objection);

    List<Objection> findRecentObjections(int limit);

    // 사건 ID 로 묶은 이의제기. 사건당 하나뿐이다
    Map<Long, Objection> findObjectionsByCaseIds(Collection<Long> caseIds);

    /** 새 판정을 저장하고 ID 를 채워 돌려준다. 반려되지 않은 판정이 이미 있으면 유니크 키가 막는다. */
    ObjectionDecision saveDecision(ObjectionDecision decision);

    /** 승인, 반려로 바뀐 상태와 보류액을 저장한다. */
    void updateDecision(ObjectionDecision decision);

    Optional<ObjectionDecision> findDecisionById(long decisionId);

    Optional<ObjectionDecision> findConfirmedDecision(long objectionId);

    Optional<ObjectionDecision> findPendingDecision(long objectionId);

    Map<Long, ObjectionDecision> findConfirmedDecisions(Collection<Long> objectionIds);

    // 목록용. 승인 대기나 확정 판정이 재량 판정인 이의제기 ID(ADR-0011). 반려된 판정은 보지 않는다
    Set<Long> findDiscretionaryObjectionIds(Collection<Long> objectionIds);

    void saveGateEvaluation(
            long objectionId, String purpose, GateResult result, GateSettings settings, LocalDateTime evaluatedAt);
}
