package com.shinhan.corebank.paymentstop.application.port.in;

import com.shinhan.corebank.paymentstop.domain.agent.AgentRun;
import com.shinhan.corebank.paymentstop.domain.agent.AgentRunStep;
import com.shinhan.corebank.paymentstop.domain.agent.ReviewDraft;
import java.util.List;
import java.util.Optional;

// 관리자 화면의 분석 실행 조회. 타임라인은 1초 폴링으로 after 뒤 단계만 받는다(D20).
public interface AgentRunQueryUseCase {

    List<AgentRun> runs(long objectionId);

    List<AgentRunStep> steps(long objectionId, long runId, int afterSeq);

    Optional<ReviewDraft> latestDraft(long objectionId);
}
