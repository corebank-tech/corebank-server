package com.shinhan.corebank.paymentstop.domain.notice;

import com.shinhan.corebank.paymentstop.domain.DecisionResult;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 시점별 통지 대상(D30, contract §7). 명의인은 문자, 그 밖은 문서로 기록한다.
 *
 * <ul>
 *   <li>접수: 명의인 접수 안내
 *   <li>보완 요청: 명의인
 *   <li>수용, 일부 지급정지 확정: 피해자와 금감원(법 제7조제2항). 지급정지가 끝나면(수용) 명의인, 피해자, 관련 금융회사(법
 *       제8조제3항)
 *   <li>불수용 확정: 명의인(당국 표준의 심사결과 통보)
 * </ul>
 */
public final class NoticePlan {

    private static final String BASIS_RECEIPT = "접수 안내(내부 기준)";
    private static final String BASIS_RESULT = "심사결과 통보(2026-05-04 당국 표준)";
    private static final String BASIS_OBJECTION_NOTICE = "이의제기 통지(법 제7조제2항)";
    private static final String BASIS_TERMINATION = "지급정지 종료 통지(법 제8조제3항)";

    private NoticePlan() {}

    public static List<ObjectionNotice> receipt(long objectionId, LocalDateTime at) {
        return List.of(holder(objectionId, NoticeKind.RECEIPT, BASIS_RECEIPT, at));
    }

    public static List<ObjectionNotice> supplementRequest(long objectionId, LocalDateTime at) {
        return List.of(holder(objectionId, NoticeKind.SUPPLEMENT_REQUEST, BASIS_RESULT, at));
    }

    /** @param stopTerminated 이 확정으로 사건의 지급정지가 끝났는지(전부 해제) */
    public static List<ObjectionNotice> decision(
            long objectionId, DecisionResult result, boolean stopTerminated, LocalDateTime at) {
        List<ObjectionNotice> notices = new ArrayList<>();
        switch (result) {
            case REJECTED -> notices.add(holder(objectionId, NoticeKind.DECISION_REJECTED, BASIS_RESULT, at));
            case ACCEPTED, PARTIAL -> {
                NoticeKind kind =
                        result == DecisionResult.ACCEPTED ? NoticeKind.DECISION_ACCEPTED : NoticeKind.DECISION_PARTIAL;
                notices.add(document(objectionId, NoticeTarget.VICTIM, kind, BASIS_OBJECTION_NOTICE, at));
                notices.add(document(objectionId, NoticeTarget.FSS, kind, BASIS_OBJECTION_NOTICE, at));
            }
        }
        if (stopTerminated) {
            notices.add(holder(objectionId, NoticeKind.STOP_TERMINATED, BASIS_TERMINATION, at));
            notices.add(document(objectionId, NoticeTarget.VICTIM, NoticeKind.STOP_TERMINATED, BASIS_TERMINATION, at));
            notices.add(document(
                    objectionId, NoticeTarget.RELATED_BANK, NoticeKind.STOP_TERMINATED, BASIS_TERMINATION, at));
        }
        return notices;
    }

    private static ObjectionNotice holder(long objectionId, NoticeKind kind, String basis, LocalDateTime at) {
        return new ObjectionNotice(null, objectionId, NoticeTarget.HOLDER, kind, NoticeChannel.SMS, basis, at);
    }

    private static ObjectionNotice document(
            long objectionId, NoticeTarget target, NoticeKind kind, String basis, LocalDateTime at) {
        return new ObjectionNotice(null, objectionId, target, kind, NoticeChannel.DOCUMENT, basis, at);
    }
}
