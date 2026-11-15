package com.shinhan.corebank.paymentstop.application.port.out;

import com.shinhan.corebank.paymentstop.domain.SupplementRequest;
import com.shinhan.corebank.paymentstop.domain.external.ExternalAgency;
import com.shinhan.corebank.paymentstop.domain.external.ExternalCheck;
import com.shinhan.corebank.paymentstop.domain.notice.ObjectionNotice;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

// 심사 진행 기록 저장소: 보완 요청, 심사 시작, 외부 확인(모의), 통지 기록.
public interface ObjectionReviewPort {

    void saveSupplementRequest(SupplementRequest request);

    List<SupplementRequest> findSupplementRequests(long objectionId);

    // 이의제기마다 가장 최근 회차
    Map<Long, SupplementRequest> findLatestSupplementRequests(Collection<Long> objectionIds);

    void markSupplementSubmitted(long objectionId, int round, LocalDateTime submittedAt, String note);

    long saveReviewSession(long objectionId, long adminId, LocalDateTime startedAt);

    Optional<ExternalCheck> findExternalCheck(long objectionId, ExternalAgency agency);

    ExternalCheck saveExternalCheck(ExternalCheck check);

    List<ExternalCheck> findExternalChecks(long objectionId);

    void saveNotices(List<ObjectionNotice> notices);

    List<ObjectionNotice> findNotices(long objectionId);
}
