package com.shinhan.corebank.paymentstop.application.port.in;

public interface RequestReviewUseCase {

    /** 재분석을 대기열에 넣는다(D35). 판정이 확정됐거나 진행 중인 분석이 있으면 거부한다. */
    long request(long objectionId, AdminActor actor);
}
