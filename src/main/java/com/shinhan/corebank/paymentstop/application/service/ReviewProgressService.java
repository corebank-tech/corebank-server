package com.shinhan.corebank.paymentstop.application.service;

import com.shinhan.corebank.common.exception.BusinessException;
import com.shinhan.corebank.paymentstop.application.port.in.AdminActor;
import com.shinhan.corebank.paymentstop.application.port.in.ReviewProgressUseCase;
import com.shinhan.corebank.paymentstop.application.port.out.ObjectionReviewPort;
import com.shinhan.corebank.paymentstop.application.port.out.PaymentStopRecordPort;
import com.shinhan.corebank.paymentstop.domain.Objection;
import com.shinhan.corebank.paymentstop.domain.ObjectionDeadline;
import com.shinhan.corebank.paymentstop.domain.SupplementRequest;
import com.shinhan.corebank.paymentstop.domain.exception.PaymentStopErrorCode;
import com.shinhan.corebank.paymentstop.domain.notice.NoticePlan;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 소명 완비 확인, 보완 요청, 심사 시작 기록(PA-10). 상태를 바꾸는 요청은 이의제기 행을 잠그고 한다. */
@Service
@RequiredArgsConstructor
public class ReviewProgressService implements ReviewProgressUseCase {

    private final PaymentStopRecordPort recordPort;
    private final ObjectionReviewPort reviewPort;
    private final ObjectionCalendar objectionCalendar;
    private final NoticeRecorder noticeRecorder;
    private final Clock clock;

    @Override
    @Transactional
    public LocalDate confirmCompleteness(long objectionId, AdminActor actor) {
        Objection objection = lock(objectionId);
        LocalDateTime now = LocalDateTime.now(clock);
        LocalDate deadline = ObjectionDeadline.reviewDeadline(now.toLocalDate(), objectionCalendar.calendar());
        objection.confirmCompleteness(actor.adminCustomerId(), now, deadline);
        recordPort.updateObjection(objection);
        return deadline;
    }

    @Override
    @Transactional
    public SupplementResult requestSupplement(
            long objectionId, String reason, List<String> missingDocuments, AdminActor actor) {
        Objection objection = lock(objectionId);
        int round = objection.nextSupplementRound();
        LocalDateTime now = LocalDateTime.now(clock);
        LocalDate due = ObjectionDeadline.supplementDue(now.toLocalDate(), round, objectionCalendar.calendar());

        objection.requestSupplement(round, due);
        recordPort.updateObjection(objection);
        reviewPort.saveSupplementRequest(new SupplementRequest(
                objectionId, round, reason.strip(), missingDocuments, due, actor.adminCustomerId(), now, null, null));
        noticeRecorder.record(NoticePlan.supplementRequest(objectionId, now));
        return new SupplementResult(round, due);
    }

    @Override
    @Transactional
    public ReviewSession startReviewSession(long objectionId, AdminActor actor) {
        if (recordPort.findObjection(objectionId).isEmpty()) {
            throw new BusinessException(PaymentStopErrorCode.OBJECTION_NOT_FOUND);
        }
        LocalDateTime now = LocalDateTime.now(clock);
        return new ReviewSession(reviewPort.saveReviewSession(objectionId, actor.adminCustomerId(), now), now);
    }

    private Objection lock(long objectionId) {
        return recordPort
                .findObjectionForUpdate(objectionId)
                .orElseThrow(() -> new BusinessException(PaymentStopErrorCode.OBJECTION_NOT_FOUND));
    }
}
