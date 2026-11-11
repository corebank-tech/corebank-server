package com.shinhan.corebank.fds.api;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/** 보류 원본과 저장된 행동 점수를 분리해 보여주는 관리자 상세 응답이다. */
public record FdsHoldDetail(
        long transferHoldId,
        String holdStatus,
        long amount,
        String recipientAccountMasked,
        ServerPolicy serverPolicy,
        Object behaviorSummary,
        @Schema(
                        description = "보류 후 비동기 점수 상태",
                        allowableValues = {"PENDING", "AVAILABLE", "UNAVAILABLE"})
                String behaviorScoreStatus,
        @Schema(
                        description = "합성 행동 모델의 보조 점수. AVAILABLE일 때만 제공하며 실제 사기 확률이 아님",
                        minimum = "0",
                        maximum = "1",
                        nullable = true)
                Double behaviorRiskScore,
        @Schema(description = "점수를 산출한 모델 버전. AVAILABLE일 때만 제공", nullable = true) String modelVersion,
        @Schema(description = "점수에 사용한 피처 버전. AVAILABLE일 때만 제공", nullable = true) String featureVersion,
        @Schema(description = "점수 참고 불가 사유. UNAVAILABLE일 때만 제공", nullable = true) String behaviorScoreUnavailableReason,
        String investigationStatus,
        List<Object> secondaryFindings,
        List<Object> callRecords) {
    public record ServerPolicy(String policyCode) {}
}
