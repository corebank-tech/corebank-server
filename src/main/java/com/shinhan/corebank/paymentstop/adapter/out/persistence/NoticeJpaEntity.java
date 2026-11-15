package com.shinhan.corebank.paymentstop.adapter.out.persistence;

import com.shinhan.corebank.paymentstop.domain.notice.NoticeChannel;
import com.shinhan.corebank.paymentstop.domain.notice.NoticeKind;
import com.shinhan.corebank.paymentstop.domain.notice.NoticeTarget;
import com.shinhan.corebank.paymentstop.domain.notice.ObjectionNotice;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "objection_notice")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class NoticeJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "notice_id")
    private Long noticeId;

    @Column(name = "objection_id", nullable = false)
    private Long objectionId;

    @Enumerated(EnumType.STRING)
    @Column(name = "target", nullable = false, length = 12)
    private NoticeTarget target;

    @Enumerated(EnumType.STRING)
    @Column(name = "kind", nullable = false, length = 20)
    private NoticeKind kind;

    @Enumerated(EnumType.STRING)
    @Column(name = "channel", nullable = false, length = 10)
    private NoticeChannel channel;

    @Column(name = "basis", nullable = false, length = 100)
    private String basis;

    @Column(name = "recorded_at", nullable = false, columnDefinition = "DATETIME(6)")
    private LocalDateTime recordedAt;

    static NoticeJpaEntity of(ObjectionNotice notice) {
        NoticeJpaEntity entity = new NoticeJpaEntity();
        entity.objectionId = notice.objectionId();
        entity.target = notice.target();
        entity.kind = notice.kind();
        entity.channel = notice.channel();
        entity.basis = notice.basis();
        entity.recordedAt = notice.recordedAt();
        return entity;
    }

    ObjectionNotice toDomain() {
        return new ObjectionNotice(noticeId, objectionId, target, kind, channel, basis, recordedAt);
    }
}
