package com.shinhan.corebank.paymentstop.domain.notice;

import java.time.LocalDateTime;

/** 통지 기록 한 건(D30). 실제로 보내지 않고 기록과 화면 표시만 한다. */
public record ObjectionNotice(
        Long noticeId,
        long objectionId,
        NoticeTarget target,
        NoticeKind kind,
        NoticeChannel channel,
        String basis,
        LocalDateTime recordedAt) {}
