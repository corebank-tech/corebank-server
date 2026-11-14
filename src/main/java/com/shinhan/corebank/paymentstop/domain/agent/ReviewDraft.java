package com.shinhan.corebank.paymentstop.domain.agent;

import com.shinhan.corebank.paymentstop.domain.gate.GateBranch;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * Agent 가 낸 판정안. 심사 참고안일 뿐 해제 근거가 아니다(D9). 해제 여부는 서버 게이트와 담당자가 정한다.
 *
 * @param content 근거 거래, 누락 소명자료, 주장, 모순, 추가 확인, 안내문 초안. 가명 처리한 값만
 */
public record ReviewDraft(
        long runId, long objectionId, GateBranch branch, Map<String, Object> content, LocalDateTime submittedAt) {}
