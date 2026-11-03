package com.shinhan.corebank.common.audit;

import static java.util.stream.Collectors.groupingBy;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

// 하루치 감사 로그를 WORM 저장소에 올린다(PH-82). 해시 도입 전 행(row_hash NULL)은 보장 대상이 아니라 뺀다.
// 경로마다 상태를 남겨 SUCCESS가 아닌 경로만 다시 올리고, 한 번 올린 사본은 덮어쓰지 않는다(#788 리뷰).
// 모든 경로가 확정되면 적재 완료 이벤트를 내보내 무결성 검증이 바로 이어지게 한다(검증 시각에 기대지 않는다).
// S3 호출 동안 DB 커넥션을 쥐지 않도록 트랜잭션을 걸지 않는다. 상태 저장은 저장할 때마다 바로 커밋된다.
@Service
@RequiredArgsConstructor
public class AuditLogArchiveService {

    private final AuditLogJpaRepository auditLogJpaRepository;
    private final AuditArchiveExecutionJpaRepository auditArchiveExecutionJpaRepository;
    private final AuditArchiveStorage auditArchiveStorage;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final ApplicationEventPublisher eventPublisher;

    public void archive(LocalDate archiveDate) {
        List<AuditArchiveExecutionJpaEntity> unfinished = startUnfinishedExecutions(archiveDate);
        if (unfinished.isEmpty()) {
            return;
        }
        Map<AuditArchivePath, List<AuditLogJpaEntity>> rowsByPath =
                findRowsOfDay(archiveDate).stream().collect(groupingBy(AuditArchivePath::of));

        List<RuntimeException> failures = new ArrayList<>();
        for (AuditArchiveExecutionJpaEntity execution : unfinished) {
            List<AuditLogJpaEntity> rows = rowsByPath.getOrDefault(execution.getArchiveType(), List.of());
            try {
                upload(execution, rows);
            } catch (RuntimeException e) {
                recordFailure(execution, e);
                failures.add(e);
            }
        }
        throwIfAnyFailed(archiveDate, failures);
        eventPublisher.publishEvent(new AuditArchiveCompletedEvent(archiveDate));
    }

    // 업로드 전에 PENDING을 커밋해 둔다. 중간에 멈춰도 다음 적재가 이 경로를 다시 본다.
    // 조회 뒤 저장이지만 배치 락이 한 번에 하나만 돌게 하고, 겹쳐도 UK가 막는다. SUCCESS를 덮으면 안 돼 upsert를 쓰지 않는다
    private List<AuditArchiveExecutionJpaEntity> startUnfinishedExecutions(LocalDate archiveDate) {
        LocalDateTime now = LocalDateTime.now(clock);
        List<AuditArchiveExecutionJpaEntity> unfinished = new ArrayList<>();
        for (AuditArchivePath path : AuditArchivePath.values()) {
            AuditArchiveExecutionJpaEntity execution = auditArchiveExecutionJpaRepository
                    .findByArchiveDateAndArchiveType(archiveDate, path)
                    .orElseGet(() -> AuditArchiveExecutionJpaEntity.pending(archiveDate, path, now));
            if (execution.isSucceeded()) {
                continue;
            }
            execution.retry(now);
            unfinished.add(auditArchiveExecutionJpaRepository.save(execution));
        }
        return unfinished;
    }

    private List<AuditLogJpaEntity> findRowsOfDay(LocalDate archiveDate) {
        return auditLogJpaRepository.findArchiveRowsOfDay(
                archiveDate.atStartOfDay(), archiveDate.plusDays(1).atStartOfDay());
    }

    // 사본이 이미 있으면(업로드 뒤 상태 저장만 실패한 경우) 내용이 같을 때만 확정한다. 다르면 원본을 두고 실패로 남긴다
    private void upload(AuditArchiveExecutionJpaEntity execution, List<AuditLogJpaEntity> rows) {
        byte[] content = toJsonLines(rows);
        String key = execution.getObjectKey();
        if (!auditArchiveStorage.putIfAbsent(key, content)) {
            byte[] archived = auditArchiveStorage.get(key).orElse(null);
            if (!Arrays.equals(archived, content)) {
                throw new IllegalStateException("이미 올린 사본과 내용이 다릅니다 - key=" + key);
            }
        }
        execution.succeed(rows.size(), LocalDateTime.now(clock));
        auditArchiveExecutionJpaRepository.save(execution);
    }

    // 실패 기록마저 못 남기면 그 예외를 원래 원인에 붙여, 업로드 실패 원인이 사라지지 않게 한다
    private void recordFailure(AuditArchiveExecutionJpaEntity execution, RuntimeException cause) {
        execution.fail(cause.getClass().getSimpleName() + ": " + cause.getMessage(), LocalDateTime.now(clock));
        try {
            auditArchiveExecutionJpaRepository.save(execution);
        } catch (RuntimeException saveFailure) {
            cause.addSuppressed(saveFailure);
        }
    }

    private byte[] toJsonLines(List<AuditLogJpaEntity> rows) {
        StringBuilder jsonLines = new StringBuilder();
        for (AuditLogJpaEntity row : rows) {
            jsonLines
                    .append(objectMapper.writeValueAsString(AuditArchiveLine.from(row)))
                    .append('\n');
        }
        return jsonLines.toString().getBytes(StandardCharsets.UTF_8);
    }

    // 실패는 상태 테이블에 남겼고, 호출자가 알 수 있게 첫 원인을 담아 다시 던진다
    private void throwIfAnyFailed(LocalDate archiveDate, List<RuntimeException> failures) {
        if (failures.isEmpty()) {
            return;
        }
        IllegalStateException failed =
                new IllegalStateException("감사 로그 적재 실패 - archiveDate=" + archiveDate, failures.getFirst());
        failures.stream().skip(1).forEach(failed::addSuppressed);
        throw failed;
    }
}
