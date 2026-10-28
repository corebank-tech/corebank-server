package com.shinhan.corebank.common.audit;

import static java.util.stream.Collectors.groupingBy;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

// 하루치 감사 로그를 WORM 저장소에 올린다(PH-82). 해시 도입 전 행(row_hash NULL)은 보장 대상이 아니라 뺀다.
// 행이 없는 날도 빈 객체를 올려, 검증 배치가 "적재했는데 0건"과 "적재하지 않음"을 구분하게 한다.
// S3 호출 동안 DB 커넥션을 쥐지 않도록 트랜잭션을 걸지 않는다.
@Service
@RequiredArgsConstructor
public class AuditLogArchiveService {

    private final AuditLogJpaRepository auditLogJpaRepository;
    private final AuditArchiveStorage auditArchiveStorage;
    private final ObjectMapper objectMapper;

    public void archive(LocalDate archiveDate) {
        List<AuditLogJpaEntity> rowsOfDay =
                auditLogJpaRepository
                        .findByRequestedAtGreaterThanEqualAndRequestedAtLessThanAndRowHashIsNotNullOrderByAuditLogId(
                                archiveDate.atStartOfDay(),
                                archiveDate.plusDays(1).atStartOfDay());
        Map<AuditArchivePath, List<AuditLogJpaEntity>> rowsByPath =
                rowsOfDay.stream().collect(groupingBy(AuditArchivePath::of));

        for (AuditArchivePath path : AuditArchivePath.values()) {
            List<AuditLogJpaEntity> rows = rowsByPath.getOrDefault(path, List.of());
            auditArchiveStorage.put(path.keyOf(archiveDate), toJsonLines(rows));
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
}
