package com.shinhan.corebank.paymentstop.application.port.in;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

// 소명 완비 확인, 보완 요청, 심사 시작 기록(PA-10).
public interface ReviewProgressUseCase {

    /** 소명 완비를 확인하고 심사 기한(확인일 + 5영업일)을 돌려준다. */
    LocalDate confirmCompleteness(long objectionId, AdminActor actor);

    /** 보완을 요청한다. 1회째 + 5영업일, 2회째 + 3영업일, 3회째는 PST0006. 명의인 통지를 기록한다. */
    SupplementResult requestSupplement(
            long objectionId, String reason, List<String> missingDocuments, AdminActor actor);

    /** 상세 화면을 연 시각을 남긴다(처리 시간 평가). */
    ReviewSession startReviewSession(long objectionId, AdminActor actor);

    record SupplementResult(int round, LocalDate supplementDue) {}

    record ReviewSession(long sessionId, LocalDateTime startedAt) {}
}
