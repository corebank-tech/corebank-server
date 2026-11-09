package com.shinhan.corebank.paymentstop.application.service;

import com.shinhan.corebank.paymentstop.application.port.out.AgentRunPort;
import com.shinhan.corebank.paymentstop.domain.agent.AgentRun;
import java.time.Clock;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 분석 대기열 등록 진입점(D35). 대리 접수(PA-04), 재분석 요청, 고객 제출 확정(PA-20)이 함께 쓴다. 별도 큐 없이 QUEUED 행 하나를
 * 넣고 워커가 조건부 UPDATE 로 가져간다. 접수와 같은 트랜잭션에 들어가야 접수만 되고 분석이 빠지는 일이 없다.
 *
 * <p>진행 중 실행은 이의제기당 하나다. 이미 있으면 새로 넣지 않고 그 실행 ID 를 돌려준다. 확인과 등록 사이에 다른 등록이 끼지 않도록
 * 호출하는 쪽은 이의제기 행을 잠갔거나(FOR UPDATE) 방금 그 이의제기를 만든 트랜잭션이어야 한다.
 */
@Component
@RequiredArgsConstructor
public class ReviewJobEnqueuer {

    private final AgentRunPort agentRunPort;
    private final Clock clock;

    @Transactional(propagation = Propagation.MANDATORY)
    public long enqueue(long objectionId, long requestedBy) {
        // 워커 킬스위치가 꺼져 있으면 접수 때 넣은 QUEUED 가 남아 있다. 하나 더 넣으면 워커가 켜질 때 판정안이 두 번 나온다
        return agentRunPort
                .findLatestRun(objectionId)
                .filter(AgentRun::isActive)
                .map(AgentRun::runId)
                .orElseGet(() -> agentRunPort.enqueue(objectionId, requestedBy, LocalDateTime.now(clock)));
    }
}
