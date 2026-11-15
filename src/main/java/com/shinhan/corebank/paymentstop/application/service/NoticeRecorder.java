package com.shinhan.corebank.paymentstop.application.service;

import com.shinhan.corebank.paymentstop.application.port.out.ObjectionReviewPort;
import com.shinhan.corebank.paymentstop.domain.notice.ObjectionNotice;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** 통지 기록(D30). 실제로 보내지 않는다. 접수, 보완 요청, 확정과 같은 트랜잭션에서만 쓴다. */
@Component
@RequiredArgsConstructor
class NoticeRecorder {

    private final ObjectionReviewPort reviewPort;

    @Transactional(propagation = Propagation.MANDATORY)
    public void record(List<ObjectionNotice> notices) {
        reviewPort.saveNotices(notices);
    }
}
