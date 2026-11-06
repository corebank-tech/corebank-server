package com.shinhan.corebank.paymentstop.domain.external;

import com.shinhan.corebank.paymentstop.domain.gate.ExternalReply;
import java.time.LocalDateTime;
import java.util.Optional;

/**
 * 외부 확인 요청 한 건(모의, PA-10). 원장 밖 사실이지만 기관 회신이라 게이트 근거로 쓴다(순서 1, 4).
 *
 * @param checkId 저장 전이면 null
 * @param reply 회신 전이면 null
 */
public record ExternalCheck(
        Long checkId,
        long objectionId,
        ExternalAgency agency,
        ExternalCheckStatus status,
        ExternalCheckReply reply,
        long requestedBy,
        LocalDateTime requestedAt,
        LocalDateTime repliedAt) {

    /** 모의 요청. 요청 즉시 지정한 회신으로 끝난다. */
    public static ExternalCheck mockReplied(
            long objectionId, ExternalAgency agency, ExternalCheckReply reply, long requestedBy, LocalDateTime at) {
        return new ExternalCheck(null, objectionId, agency, ExternalCheckStatus.REPLIED, reply, requestedBy, at, at);
    }

    /** 게이트 입력이 되는 회신. 회신 전이거나 해당 기록 없음이면 비어 있다. */
    public Optional<ExternalReply> gateReply() {
        return status == ExternalCheckStatus.REPLIED && reply != null ? reply.gateReply() : Optional.empty();
    }
}
