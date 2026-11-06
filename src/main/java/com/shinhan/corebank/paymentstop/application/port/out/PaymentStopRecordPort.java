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

// paymentstop 도메인의 사건, 이의제기, 판정, 게이트 기록 저장소.
public interface PaymentStopRecordPort {

    /** 사건과 다툼 거래를 저장한다. 다른 사건에 이미 있는 다툼 거래면 PST0303. */
    PaymentStopCase saveNewCase(PaymentStopCase paymentStopCase);

    Optional<PaymentStopCase> findCase(long caseId);

    // 목록용. 사건과 다툼 거래를 사건 수와 무관하게 두 번의 조회로 읽는다
    Map<Long, PaymentStopCase> findCases(Collection<Long> caseIds);

    void updateCaseStatus(PaymentStopCase paymentStopCase);

    boolean existsObjectionByCaseId(long caseId);

    /** 사건당 하나뿐이라 동시 접수는 유니크 제약이 막는다. 걸리면 PST0304. */
    Objection saveNewObjection(Objection objection);

    Optional<Objection> findObjection(long objectionId);

    // 판정 확정 중복을 막으려고 이의제기 행을 잠근다. 반드시 트랜잭션 안에서 부른다
    Optional<Objection> findObjectionForUpdate(long objectionId);

    void updateObjectionStatus(Objection objection);

    List<Objection> findRecentObjections(int limit);

    void saveDecision(ObjectionDecision decision);

    Optional<ObjectionDecision> findDecision(long objectionId);

    void saveGateEvaluation(
            long objectionId, String purpose, GateResult result, GateSettings settings, LocalDateTime evaluatedAt);
}
