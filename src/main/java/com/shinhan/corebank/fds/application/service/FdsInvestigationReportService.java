package com.shinhan.corebank.fds.application.service;

import com.shinhan.corebank.common.exception.BusinessException;
import com.shinhan.corebank.fds.api.FdsInvestigationJob;
import com.shinhan.corebank.fds.application.port.out.FdsInvestigationJobPort;
import com.shinhan.corebank.fds.domain.exception.FdsErrorCode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Python 콜백 보고서만 검증·저장하고 RUNNING 작업의 완료 전이를 소유한다. */
@Service
@RequiredArgsConstructor
public class FdsInvestigationReportService {
    private static final String SCHEMA_VERSION = "p6-investigation-v1";
    private static final int MAX_REPORT_BYTES = 16 * 1024;
    private static final Set<String> FINDING_CODES = Set.of(
            "REGULAR_SPLIT_AMOUNTS",
            "MULTIPLE_SOURCE_ACCOUNTS",
            "LIMITED_RECIPIENT_HISTORY",
            "BEHAVIOR_TRANSFER_MISMATCH");
    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    private final FdsInvestigationJobPort jobs;
    private final Clock clock;

    @Transactional
    public Acknowledgement submit(String investigationId, String reportJson) {
        FdsInvestigationJob job = jobs.findByInvestigationId(investigationId);
        if (job == null) throw new BusinessException(FdsErrorCode.NOT_FOUND);
        int callbackAttemptNo = validate(reportJson, job);
        // DB 최신값이 아니라 Python이 실행 시작 때 받은 세대로 늦은 콜백을 차단한다.
        jobs.completeWithReport(
                job.transferHoldId(),
                investigationId,
                callbackAttemptNo,
                SCHEMA_VERSION,
                reportJson,
                sha256(reportJson),
                now());
        return new Acknowledgement(investigationId, "COMPLETED");
    }

    private int validate(String json, FdsInvestigationJob job) {
        if (json == null || json.getBytes(StandardCharsets.UTF_8).length > MAX_REPORT_BYTES) invalid();
        JsonNode root;
        try {
            root = MAPPER.readTree(json);
            if (job.transferHoldId() != root.path("transferHoldId").asLong(-1)
                    || !root.path("attemptNo").isIntegralNumber()
                    || root.path("attemptNo").asInt(-1) <= 0
                    || !"COMPLETED".equals(root.path("status").asText())
                    || !"MANUAL_CUSTOMER_CONFIRMATION"
                            .equals(root.path("recommendedAction").asText())
                    || !validText(root.path("summary"), 1, 500)
                    || !validArray(root.path("secondaryFindings"), 4)
                    || !validStringArray(root.path("missingInformation"), 5, 100)
                    || !validStringArray(root.path("suggestedQuestions"), 3, 200)) invalid();
            for (JsonNode finding : root.path("secondaryFindings")) {
                if (!FINDING_CODES.contains(finding.path("code").asText())
                        || !validText(finding.path("description"), 1, 300)
                        || !Set.of("OBSERVED", "INFERRED")
                                .contains(finding.path("confidence").asText())) invalid();
            }
        } catch (BusinessException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            invalid();
            return -1;
        }
        // DB 장애는 잘못된 JSON으로 축약하지 않고 워커가 재시도할 수 있게 그대로 전파한다.
        Set<Long> provided = jobs.findEvidenceIds(job.investigationId());
        for (JsonNode finding : root.path("secondaryFindings")) {
            if (!validEvidence(finding.path("evidenceTransferIds"), provided)) invalid();
        }
        return root.path("attemptNo").asInt();
    }

    private static boolean validEvidence(JsonNode node, Set<Long> provided) {
        if (!validArray(node, 10)) return false;
        Set<Long> seen = new HashSet<>();
        for (JsonNode value : node) {
            long id = value.asLong(-1);
            if (!value.isIntegralNumber() || id <= 0 || !provided.contains(id) || !seen.add(id)) return false;
        }
        return true;
    }

    private static boolean validStringArray(JsonNode node, int maxItems, int maxLength) {
        if (!validArray(node, maxItems)) return false;
        for (JsonNode value : node) if (!validText(value, 1, maxLength)) return false;
        return true;
    }

    private static boolean validArray(JsonNode node, int maxItems) {
        return node.isArray() && node.size() <= maxItems;
    }

    private static boolean validText(JsonNode node, int min, int max) {
        return node.isTextual()
                && node.textValue().length() >= min
                && node.textValue().length() <= max;
    }

    private static void invalid() {
        throw new BusinessException(FdsErrorCode.INVALID_INVESTIGATION_REPORT);
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of()
                    .formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock);
    }

    public record Acknowledgement(String investigationId, String status) {}
}
