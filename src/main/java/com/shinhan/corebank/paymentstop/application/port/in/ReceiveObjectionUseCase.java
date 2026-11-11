package com.shinhan.corebank.paymentstop.application.port.in;

public interface ReceiveObjectionUseCase {

    /** 영업점 대리 접수. 이의제기 ID를 돌려준다. 분석 작업을 대기열에 넣고 명의인 접수 통지를 기록한다. */
    long receive(Command command, AdminActor actor);

    record Command(
            long caseId,
            int clause,
            String statement,
            boolean simplifiedRequested,
            boolean idCopyConfirmed,
            boolean litigationPending) {}
}
