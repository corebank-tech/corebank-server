package com.shinhan.corebank.paymentstop.application.port.in;

public interface ReceiveObjectionUseCase {

    /** 이의제기를 접수한다. 이의제기 ID를 돌려준다. */
    long receive(Command command, AdminActor actor);

    record Command(
            long caseId,
            int clause,
            String statement,
            boolean simplifiedRequested,
            boolean idCopyConfirmed,
            boolean litigationPending) {}
}
