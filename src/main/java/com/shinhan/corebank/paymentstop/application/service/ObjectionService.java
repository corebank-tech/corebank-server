package com.shinhan.corebank.paymentstop.application.service;

import com.shinhan.corebank.common.exception.BusinessException;
import com.shinhan.corebank.paymentstop.application.port.in.AdminActor;
import com.shinhan.corebank.paymentstop.application.port.in.ReceiveObjectionUseCase;
import com.shinhan.corebank.paymentstop.application.port.out.PaymentStopRecordPort;
import com.shinhan.corebank.paymentstop.domain.CaseStatus;
import com.shinhan.corebank.paymentstop.domain.Objection;
import com.shinhan.corebank.paymentstop.domain.ObjectionChannel;
import com.shinhan.corebank.paymentstop.domain.PaymentStopCase;
import com.shinhan.corebank.paymentstop.domain.exception.PaymentStopErrorCode;
import com.shinhan.corebank.paymentstop.domain.notice.NoticePlan;
import java.time.Clock;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ObjectionService implements ReceiveObjectionUseCase {

    private final PaymentStopRecordPort recordPort;
    private final ReviewJobEnqueuer reviewJobEnqueuer;
    private final NoticeRecorder noticeRecorder;
    private final Clock clock;

    @Override
    @Transactional
    public long receive(Command command, AdminActor actor) {
        PaymentStopCase paymentStopCase = recordPort
                .findCase(command.caseId())
                .orElseThrow(() -> new BusinessException(PaymentStopErrorCode.CASE_NOT_FOUND));
        return receive(
                paymentStopCase,
                command.clause(),
                command.statement(),
                command.simplifiedRequested(),
                command.idCopyConfirmed(),
                command.litigationPending(),
                actor.adminCustomerId(),
                ObjectionChannel.BRANCH);
    }

    /** 영업점과 비대면 접수가 함께 쓴다. 접수, 분석 대기열, 명의인 접수 통지를 한 트랜잭션에 넣는다. */
    @Transactional(propagation = Propagation.MANDATORY)
    public long receive(
            PaymentStopCase paymentStopCase,
            int clause,
            String statement,
            boolean simplifiedRequested,
            boolean idCopyConfirmed,
            boolean litigationPending,
            long receivedBy,
            ObjectionChannel channel) {
        // 해제된 사건은 지급정지가 아니다. 고객 경로는 이 오류로 존재 여부를 가린다
        if (paymentStopCase.getStatus() == CaseStatus.RELEASED) {
            throw new BusinessException(PaymentStopErrorCode.CASE_NOT_FOUND);
        }
        // 빠른 거절용 확인이다. 동시 접수는 uk_objection_case 가 막는다
        if (recordPort.existsObjectionByCaseId(paymentStopCase.getCaseId())) {
            throw new BusinessException(PaymentStopErrorCode.OBJECTION_ALREADY_RECEIVED);
        }

        LocalDateTime now = LocalDateTime.now(clock);
        Objection objection = Objection.receive(
                paymentStopCase,
                clause,
                statement,
                simplifiedRequested,
                idCopyConfirmed,
                litigationPending,
                receivedBy,
                channel,
                now);

        long objectionId = recordPort.saveNewObjection(objection).getObjectionId();

        // 접수와 함께 분석을 대기열에 넣는다(D35). 법정 기한 기산은 여기가 아니라 담당자의 소명 완비 확인이다(D12)
        reviewJobEnqueuer.enqueue(objectionId, receivedBy);
        noticeRecorder.record(NoticePlan.receipt(objectionId, now));

        return objectionId;
    }
}
