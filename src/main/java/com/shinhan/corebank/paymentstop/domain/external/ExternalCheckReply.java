package com.shinhan.corebank.paymentstop.domain.external;

import com.shinhan.corebank.paymentstop.domain.gate.ExternalReply;
import java.util.Optional;

/** 외부 확인 회신 값. 게이트가 쓰는 회신은 {@link ExternalReply}이고, 해당 기록 없음(NO_RECORD)은 게이트에 영향이 없다. */
public enum ExternalCheckReply {
    INVOLVEMENT_SUSPECTED(ExternalReply.INVOLVEMENT_SUSPECTED),
    DOCUMENT_FORGED(ExternalReply.DOCUMENT_FORGED),
    OWNER_UNRELATED(ExternalReply.OWNER_UNRELATED),
    NO_RECORD(null);

    private final ExternalReply gateReply;

    ExternalCheckReply(ExternalReply gateReply) {
        this.gateReply = gateReply;
    }

    public Optional<ExternalReply> gateReply() {
        return Optional.ofNullable(gateReply);
    }
}
