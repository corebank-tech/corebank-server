package com.shinhan.corebank.common.audit;

import java.time.LocalDate;

// 그날의 모든 경로 사본이 WORM 저장소에 확정됐다는 알림. 무결성 검증이 이걸 받아 바로 그날을 대조한다(PH-82)
public record AuditArchiveCompletedEvent(LocalDate archiveDate) {}
