package com.shinhan.corebank.fds.application.service;

import com.shinhan.corebank.common.exception.BusinessException;
import com.shinhan.corebank.fds.api.FdsInvestigationInput;
import com.shinhan.corebank.fds.api.FdsInvestigationJob;
import com.shinhan.corebank.fds.application.port.out.FdsInvestigationAgentPort;
import com.shinhan.corebank.fds.application.port.out.FdsInvestigationJobPort;
import com.shinhan.corebank.fds.domain.exception.FdsErrorCode;
import com.shinhan.corebank.transfer.api.TransferHeld;
import com.shinhan.corebank.transfer.api.TransferHoldForFds;
import com.shinhan.corebank.transfer.api.TransferHoldUseCase;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** TransferHeld를 조사 작업으로 내구화하고 별도 워커 시도를 조건부 전이한다. */
@Service
@RequiredArgsConstructor
public class FdsInvestigationJobService {
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final Set<String> TERMINAL_HOLD_STATUSES =
            Set.of("EXECUTED", "REJECTED", "FAILED", "CANCELLED", "EXPIRED");
    private static final Set<String> FINDING_CODES = Set.of(
            "REGULAR_SPLIT_AMOUNTS",
            "MULTIPLE_SOURCE_ACCOUNTS",
            "LIMITED_RECIPIENT_HISTORY",
            "BEHAVIOR_TRANSFER_MISMATCH");
    private static final int MAX_REPORT_BYTES = 16 * 1024;
    private static final JsonMapper REPORT_MAPPER = JsonMapper.builder().build();

    private final FdsInvestigationJobPort jobs;
    private final FdsInvestigationInputService inputs;
    private final TransferHoldUseCase holds;
    private final Clock clock;

    public FdsInvestigationJob register(TransferHeld event) {
        LocalDateTime now = now();
        return jobs.createIfAbsent(event.transferHoldId(), UUID.randomUUID().toString(), now);
    }

    public int recoverExpired() {
        return jobs.recoverExpired(now());
    }

    public void runDue(FdsInvestigationAgentPort agent, int limit) {
        LocalDateTime now = now();
        for (Long holdId : jobs.findDue(now, limit)) {
            runOne(agent, holdId, now);
        }
    }

    void runOne(FdsInvestigationAgentPort agent, long holdId, LocalDateTime startedAt) {
        if (!jobs.claim(holdId, startedAt, startedAt.plusSeconds(70))) return;
        FdsInvestigationJob job = jobs.findByHoldId(holdId);
        if (job == null) return;
        try {
            TransferHoldForFds hold = holds.findForFds(holdId);
            if (closed(hold)) {
                jobs.fail(holdId, "HOLD_CLOSED", now());
                return;
            }
            FdsInvestigationInput input = inputs.build(holdId, job.investigationId());
            FdsInvestigationAgentPort.Result result;
            try {
                result = agent.investigate(input);
            } catch (RuntimeException exception) {
                finishTransientFailure(job, "AGENT_UNAVAILABLE");
                return;
            }
            ValidatedReport report;
            try {
                report = validate(result, job);
            } catch (BusinessException exception) {
                jobs.fail(holdId, exception.getErrorCode().getCode(), now());
                return;
            }
            if (closed(holds.findForFds(holdId))) {
                jobs.fail(holdId, "HOLD_CLOSED", now());
                return;
            }
            try {
                jobs.completeWithReport(
                        holdId,
                        job.investigationId(),
                        report.schemaVersion(),
                        report.json(),
                        sha256(report.json()),
                        now());
            } catch (BusinessException exception) {
                jobs.fail(holdId, exception.getErrorCode().getCode(), now());
            }
        } catch (BusinessException exception) {
            finishTransientFailure(
                    job, "HOLD_OR_INPUT_" + exception.getErrorCode().getCode());
        } catch (RuntimeException exception) {
            finishTransientFailure(job, "INVESTIGATION_INFRA_FAILURE");
        }
    }

    private void finishTransientFailure(FdsInvestigationJob job, String reason) {
        LocalDateTime now = now();
        if (job.attemptCount() >= 3) {
            jobs.fail(job.transferHoldId(), reason, now);
            return;
        }
        Duration delay = job.attemptCount() == 1 ? Duration.ofMinutes(1) : Duration.ofMinutes(5);
        jobs.retry(job.transferHoldId(), now.plus(delay), reason, now);
    }

    private ValidatedReport validate(FdsInvestigationAgentPort.Result result, FdsInvestigationJob job) {
        if (result == null
                || result.schemaVersion() == null
                || result.reportJson() == null
                || result.reportJson().getBytes(StandardCharsets.UTF_8).length > MAX_REPORT_BYTES) {
            throw new BusinessException(FdsErrorCode.INVALID_INVESTIGATION_REPORT);
        }
        try {
            JsonNode root = REPORT_MAPPER.readTree(result.reportJson());
            if (!result.schemaVersion().equals(root.path("schemaVersion").asText())
                    || !job.investigationId()
                            .equals(root.path("investigationId").asText())
                    || job.transferHoldId() != root.path("transferHoldId").asLong(-1)
                    || !"COMPLETED".equals(root.path("status").asText())
                    || !validText(root.path("summary"), 1, 500)
                    || !validArray(root.path("secondaryFindings"), 4)
                    || !validStringArray(root.path("missingInformation"), 5, 100)
                    || !validStringArray(root.path("suggestedQuestions"), 3, 200)) {
                throw new BusinessException(FdsErrorCode.INVALID_INVESTIGATION_REPORT);
            }
            Set<Long> providedEvidenceIds = jobs.findEvidenceIds(job.investigationId());
            for (JsonNode finding : root.path("secondaryFindings")) {
                if (!FINDING_CODES.contains(finding.path("code").asText())
                        || !validText(finding.path("description"), 1, 300)
                        || !Set.of("OBSERVED", "INFERRED")
                                .contains(finding.path("confidence").asText())
                        || !validEvidenceIds(finding.path("evidenceTransferIds"), providedEvidenceIds)) {
                    throw new BusinessException(FdsErrorCode.INVALID_INVESTIGATION_REPORT);
                }
            }
            return new ValidatedReport(result.schemaVersion(), result.reportJson());
        } catch (BusinessException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new BusinessException(FdsErrorCode.INVALID_INVESTIGATION_REPORT);
        }
    }

    private static boolean validEvidenceIds(JsonNode node, Set<Long> providedIds) {
        if (!validArray(node, 10)) return false;
        Set<Long> seen = new HashSet<>();
        for (JsonNode value : node) {
            if (!value.canConvertToLong()
                    || value.asLong() <= 0
                    || !providedIds.contains(value.asLong())
                    || !seen.add(value.asLong())) return false;
        }
        return true;
    }

    private static boolean closed(TransferHoldForFds hold) {
        return hold == null || TERMINAL_HOLD_STATUSES.contains(hold.status());
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

    private static String sha256(String value) {
        try {
            return HexFormat.of()
                    .formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock.withZone(KST));
    }

    private record ValidatedReport(String schemaVersion, String json) {}
}
