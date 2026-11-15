package com.shinhan.corebank.paymentstop.application.service;

import com.shinhan.corebank.common.exception.BusinessException;
import com.shinhan.corebank.paymentstop.application.port.in.AdminActor;
import com.shinhan.corebank.paymentstop.application.port.in.ExternalCheckUseCase;
import com.shinhan.corebank.paymentstop.application.port.out.ObjectionReviewPort;
import com.shinhan.corebank.paymentstop.application.port.out.PaymentStopRecordPort;
import com.shinhan.corebank.paymentstop.domain.Objection;
import com.shinhan.corebank.paymentstop.domain.exception.PaymentStopErrorCode;
import com.shinhan.corebank.paymentstop.domain.external.ExternalAgency;
import com.shinhan.corebank.paymentstop.domain.external.ExternalCheck;
import com.shinhan.corebank.paymentstop.domain.external.ExternalCheckReply;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 외부 확인 요청(모의). 막는 쪽 행동이라 확정 전 언제나 할 수 있다(D9). 같은 기관 중복 요청은 이의제기 행 락으로 줄 세워 기존 행을
 * 돌려준다.
 */
@Service
@RequiredArgsConstructor
public class ExternalCheckService implements ExternalCheckUseCase {

    private final PaymentStopRecordPort recordPort;
    private final ObjectionReviewPort reviewPort;
    private final Clock clock;

    @Override
    @Transactional
    public ExternalCheck request(
            long objectionId, ExternalAgency agency, ExternalCheckReply mockReply, AdminActor actor) {
        Objection objection = recordPort
                .findObjectionForUpdate(objectionId)
                .orElseThrow(() -> new BusinessException(PaymentStopErrorCode.OBJECTION_NOT_FOUND));
        Optional<ExternalCheck> existing = reviewPort.findExternalCheck(objectionId, agency);
        if (existing.isPresent()) {
            return existing.get();
        }
        objection.requireNotDecided();
        return reviewPort.saveExternalCheck(ExternalCheck.mockReplied(
                objectionId, agency, mockReply, actor.adminCustomerId(), LocalDateTime.now(clock)));
    }

    @Override
    @Transactional(readOnly = true)
    public List<ExternalCheck> list(long objectionId) {
        if (recordPort.findObjection(objectionId).isEmpty()) {
            throw new BusinessException(PaymentStopErrorCode.OBJECTION_NOT_FOUND);
        }
        return reviewPort.findExternalChecks(objectionId);
    }
}
