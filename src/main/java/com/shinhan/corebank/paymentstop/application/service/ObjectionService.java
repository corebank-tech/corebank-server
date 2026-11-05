package com.shinhan.corebank.paymentstop.application.service;

import com.shinhan.corebank.common.exception.BusinessException;
import com.shinhan.corebank.paymentstop.application.port.in.AdminActor;
import com.shinhan.corebank.paymentstop.application.port.in.ReceiveObjectionUseCase;
import com.shinhan.corebank.paymentstop.application.port.out.PaymentStopRecordPort;
import com.shinhan.corebank.paymentstop.domain.Objection;
import com.shinhan.corebank.paymentstop.domain.PaymentStopCase;
import com.shinhan.corebank.paymentstop.domain.exception.PaymentStopErrorCode;
import java.time.Clock;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ObjectionService implements ReceiveObjectionUseCase {

    private final PaymentStopRecordPort recordPort;
    private final Clock clock;

    @Override
    @Transactional
    public long receive(Command command, AdminActor actor) {
        PaymentStopCase paymentStopCase = recordPort
                .findCase(command.caseId())
                .orElseThrow(() -> new BusinessException(PaymentStopErrorCode.CASE_NOT_FOUND));

        // 빠른 거절용 확인이다. 동시 접수는 uk_objection_case 가 막는다
        if (recordPort.existsObjectionByCaseId(command.caseId())) {
            throw new BusinessException(PaymentStopErrorCode.OBJECTION_ALREADY_RECEIVED);
        }

        Objection objection = Objection.receive(
                paymentStopCase,
                command.clause(),
                command.statement(),
                command.simplifiedRequested(),
                command.idCopyConfirmed(),
                command.litigationPending(),
                actor.adminCustomerId(),
                LocalDateTime.now(clock));

        return recordPort.saveNewObjection(objection).getObjectionId();
    }
}
