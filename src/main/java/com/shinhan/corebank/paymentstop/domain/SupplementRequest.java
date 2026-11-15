package com.shinhan.corebank.paymentstop.domain;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 보완 요청 한 회차(D32). 판정 결과가 아니라 보완 기록이다.
 *
 * @param missingDocuments 요청한 서류 이름. 명의인에게 그대로 보인다
 * @param submittedAt 명의인이 보완 자료를 낸 시각. 아직이면 null
 */
public record SupplementRequest(
        long objectionId,
        int round,
        String reason,
        List<String> missingDocuments,
        LocalDate dueDate,
        long requestedBy,
        LocalDateTime requestedAt,
        LocalDateTime submittedAt,
        String submitNote) {

    public SupplementRequest {
        missingDocuments = missingDocuments == null ? List.of() : List.copyOf(missingDocuments);
    }
}
