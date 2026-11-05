package com.shinhan.corebank.paymentstop.application.service;

import com.shinhan.corebank.paymentstop.application.port.out.AgentRunPort;
import java.time.Clock;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 분석 대기열 등록 진입점(D35). 대리 접수(PA-04), 재분석 요청, 고객 제출 확정(PA-20)이 함께 쓴다. 별도 큐 없이 QUEUED 행 하나를
 * 넣고 워커가 조건부 UPDATE 로 가져간다. 접수와 같은 트랜잭션에 들어가야 접수만 되고 분석이 빠지는 일이 없다.
 */
@Component
@RequiredArgsConstructor
public class ReviewJobEnqueuer {

    private final AgentRunPort agentRunPort;
    private final Clock clock;

    @Transactional(propagation = Propagation.MANDATORY)
    public long enqueue(long objectionId, long requestedBy) {
        return agentRunPort.enqueue(objectionId, requestedBy, LocalDateTime.now(clock));
    }
}
