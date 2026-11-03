package com.shinhan.corebank.common.audit;

import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.shinhan.corebank.batch.api.BatchExecutionLockPort;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

class AuditLogArchiveSchedulerTest {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    // 2030-01-16 00:30 KST
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2030-01-15T15:30:00Z"), SEOUL);

    private final AuditLogArchiveService auditLogArchiveService = mock(AuditLogArchiveService.class);
    private final BatchExecutionLockPort batchExecutionLockPort = mock(BatchExecutionLockPort.class);
    private final AuditLogArchiveScheduler scheduler =
            new AuditLogArchiveScheduler(auditLogArchiveService, batchExecutionLockPort, CLOCK);

    @Test
    @DisplayName("최근 7일을 오래된 날부터 올리고, 한 날이 실패해도 나머지 날을 계속 올린 뒤 락을 푼다")
    void archiveRecentDays_continuesAfterFailedDay() {
        given(batchExecutionLockPort.tryAcquire("AUDIT_LOG_ARCHIVE")).willReturn(true);
        willThrow(new IllegalStateException("업로드 실패"))
                .given(auditLogArchiveService)
                .archive(LocalDate.of(2030, 1, 12));

        scheduler.archiveRecentDays();

        InOrder order = inOrder(auditLogArchiveService);
        for (int day = 9; day <= 15; day++) {
            order.verify(auditLogArchiveService).archive(LocalDate.of(2030, 1, day));
        }
        verify(batchExecutionLockPort).release("AUDIT_LOG_ARCHIVE");
    }
}
