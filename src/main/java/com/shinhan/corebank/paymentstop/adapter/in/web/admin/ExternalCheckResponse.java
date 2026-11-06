package com.shinhan.corebank.paymentstop.adapter.in.web.admin;

import com.shinhan.corebank.paymentstop.domain.external.ExternalCheck;
import java.time.LocalDateTime;

public record ExternalCheckResponse(
        long checkId, String agency, String status, String reply, LocalDateTime requestedAt, LocalDateTime repliedAt) {

    static ExternalCheckResponse from(ExternalCheck check) {
        return new ExternalCheckResponse(
                check.checkId(),
                check.agency().name(),
                check.status().name(),
                check.reply() == null ? null : check.reply().name(),
                check.requestedAt(),
                check.repliedAt());
    }
}
