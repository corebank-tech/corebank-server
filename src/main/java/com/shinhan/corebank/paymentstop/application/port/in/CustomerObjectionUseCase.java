package com.shinhan.corebank.paymentstop.application.port.in;

import com.shinhan.corebank.paymentstop.domain.DecisionResult;
import com.shinhan.corebank.paymentstop.domain.ObjectionStatus;
import com.shinhan.corebank.paymentstop.domain.document.ObjectionDocument;
import com.shinhan.corebank.paymentstop.domain.document.UploadFile;
import java.time.LocalDate;
import java.util.List;

/**
 * 고객 비대면 이의제기(D34, PA-20). 본인 계좌에 걸린 사건만 다룬다. 남의 사건과 없는 사건은 같은 오류로 거부해 존재 여부를 드러내지
 * 않는다(AGENTS 규칙 4).
 */
public interface CustomerObjectionUseCase {

    List<MyCase> myCases(long customerId);

    /** 신청서와 신분증 사본, 증빙을 한 번에 낸다. 분석 작업 1건을 대기열에 넣고 접수 통지를 기록한다. */
    long submit(long customerId, SubmitCommand command);

    /** 보완 자료를 낸다. 보완 요청 상태에서만 받고, 분석 작업 1건을 대기열에 넣는다. */
    List<ObjectionDocument> submitSupplement(long customerId, long objectionId, List<UploadFile> evidence, String note);

    record SubmitCommand(
            long caseId,
            int clause,
            String statement,
            boolean simplifiedRequested,
            boolean litigationPending,
            UploadFile idCopy,
            List<UploadFile> evidence) {}

    /**
     * @param objectionId 아직 이의제기하지 않았으면 null
     * @param supplementDue 보완 요청 중일 때만 보완 기한, 아니면 null
     * @param missingDocuments 보완 요청 중이면 요청 서류, 아니면 빈 목록
     * @param decisionResult 확정된 심사결과. 확정 전이면 null
     */
    record MyCase(
            long caseId,
            String accountNumberMasked,
            String caseStatus,
            long disputedTotal,
            LocalDate objectionDeadline,
            Long objectionId,
            ObjectionStatus objectionStatus,
            LocalDate supplementDue,
            List<String> missingDocuments,
            DecisionResult decisionResult) {}
}
