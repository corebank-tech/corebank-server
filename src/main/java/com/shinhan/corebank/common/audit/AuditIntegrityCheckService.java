package com.shinhan.corebank.common.audit;

import static java.util.stream.Collectors.toSet;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

// 하루치 WORM 사본과 DB 감사 행을 대조해 불일치를 남긴다(PH-82). 사본 해시는 바꿀 수 없으니 정본이고,
// DB 행은 다시 계산한 해시로 비교한다. row_hash까지 함께 고쳐도 사본과 달라 잡힌다.
// 불일치 기록은 증거라 지우지 않고, 이미 남긴 것은 건너뛴 채 새로 찾은 것만 쌓는다. 되돌려 놔도 탐지 기록이 남는다.
// 저장소를 읽는 동안 DB 커넥션을 쥐지 않도록, 기록하는 부분만 트랜잭션으로 묶는다.
@Service
@RequiredArgsConstructor
@EnableConfigurationProperties(AuditIntegrityProperties.class)
public class AuditIntegrityCheckService {

    private final AuditLogJpaRepository auditLogJpaRepository;
    private final AuditIntegrityMismatchJpaRepository auditIntegrityMismatchJpaRepository;
    private final AuditArchiveExecutionJpaRepository auditArchiveExecutionJpaRepository;
    private final AuditArchiveStorage auditArchiveStorage;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transactionTemplate;
    private final AuditIntegrityProperties auditIntegrityProperties;
    private final Clock clock;

    // 모든 경로 적재가 SUCCESS인 날만 대조 대상이다. 적재 중이거나 실패한 날은 사본이 비어 있어 오탐이 난다
    public boolean isArchiveCompleted(LocalDate archiveDate) {
        long succeeded =
                auditArchiveExecutionJpaRepository.countByArchiveDateAndStatus(archiveDate, AuditArchiveStatus.SUCCESS);
        return succeeded == AuditArchivePath.values().length;
    }

    public int check(LocalDate archiveDate) {
        Map<Long, String> archivedHashById = readArchivedHashes(archiveDate);
        List<AuditLogJpaEntity> rowsOfDay =
                auditLogJpaRepository.findByRequestedAtGreaterThanEqualAndRequestedAtLessThan(
                        archiveDate.atStartOfDay(), archiveDate.plusDays(1).atStartOfDay());

        List<AuditLogJpaEntity> rowsToCheck = rowsOfDay.stream()
                .filter(row -> !isBeforeRowHashIntroduced(row))
                .toList();

        List<AuditIntegrityMismatchJpaEntity> mismatches =
                compare(archiveDate, rowsToCheck, archivedHashById, LocalDateTime.now(clock));

        transactionTemplate.executeWithoutResult(status -> saveNewMismatches(archiveDate, mismatches));
        return mismatches.size();
    }

    private void saveNewMismatches(LocalDate archiveDate, List<AuditIntegrityMismatchJpaEntity> mismatches) {
        Set<MismatchKey> alreadyRecorded = auditIntegrityMismatchJpaRepository.findByArchiveDate(archiveDate).stream()
                .map(MismatchKey::of)
                .collect(toSet());
        List<AuditIntegrityMismatchJpaEntity> newMismatches = mismatches.stream()
                .filter(mismatch -> !alreadyRecorded.contains(MismatchKey.of(mismatch)))
                .toList();
        auditIntegrityMismatchJpaRepository.saveAll(newMismatches);
    }

    // 해시 도입 전 행은 해시가 없어도 정상이다. 도입 뒤에 해시 없이 들어온 행은 위조라 그대로 대조한다
    private boolean isBeforeRowHashIntroduced(AuditLogJpaEntity row) {
        return row.getRowHash() == null && row.getRequestedAt().isBefore(auditIntegrityProperties.rowHashSince());
    }

    // 사본이 없는 날은 빈 맵이 되어, 그날 DB 행이 전부 NOT_ARCHIVED로 남는다
    private Map<Long, String> readArchivedHashes(LocalDate archiveDate) {
        Map<Long, String> archivedHashById = new HashMap<>();
        for (AuditArchivePath path : AuditArchivePath.values()) {
            auditArchiveStorage.get(path.keyOf(archiveDate)).ifPresent(content -> objectMapper
                    .readerFor(AuditArchiveLine.class)
                    .<AuditArchiveLine>readValues(content)
                    .forEachRemaining(line -> archivedHashById.put(line.auditLogId(), line.rowHash())));
        }
        return archivedHashById;
    }

    private List<AuditIntegrityMismatchJpaEntity> compare(
            LocalDate archiveDate,
            List<AuditLogJpaEntity> rowsOfDay,
            Map<Long, String> archivedHashById,
            LocalDateTime detectedAt) {
        Map<Long, String> notYetMatched = new HashMap<>(archivedHashById);
        List<AuditIntegrityMismatchJpaEntity> mismatches = new ArrayList<>();
        for (AuditLogJpaEntity row : rowsOfDay) {
            String archivedHash = notYetMatched.remove(row.getAuditLogId());
            if (archivedHash == null) {
                mismatches.add(AuditIntegrityMismatchJpaEntity.of(
                        archiveDate, row.getAuditLogId(), AuditIntegrityMismatchType.NOT_ARCHIVED, detectedAt));
            } else if (!archivedHash.equals(AuditLogRowHash.of(row))) {
                mismatches.add(AuditIntegrityMismatchJpaEntity.of(
                        archiveDate, row.getAuditLogId(), AuditIntegrityMismatchType.TAMPERED, detectedAt));
            }
        }
        for (Long missingId : notYetMatched.keySet()) {
            mismatches.add(AuditIntegrityMismatchJpaEntity.of(
                    archiveDate, missingId, AuditIntegrityMismatchType.MISSING_IN_DB, detectedAt));
        }
        return mismatches;
    }

    private record MismatchKey(Long auditLogId, AuditIntegrityMismatchType mismatchType) {
        static MismatchKey of(AuditIntegrityMismatchJpaEntity mismatch) {
            return new MismatchKey(mismatch.getAuditLogId(), mismatch.getMismatchType());
        }
    }
}
