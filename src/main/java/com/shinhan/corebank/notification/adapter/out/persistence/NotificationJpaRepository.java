package com.shinhan.corebank.notification.adapter.out.persistence;

import java.time.LocalDateTime;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NotificationJpaRepository extends JpaRepository<NotificationJpaEntity, Long> {

    /**
     * 알림을 미읽음으로 넣되, 같은 (type, ref_id)가 이미 있으면 아무것도 바꾸지 않는다. 구독자는 같은
     * 이벤트를 두 번 받을 수 있어(At-Least-Once) uk_noti_type_ref 가 두 번째를 판정한다. 조회 후 INSERT 는
     * 동시 수신에 뚫리고, INSERT IGNORE 는 S락이라 승격 데드락이 날 수 있어 쓰지 않는다.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
            value =
                    """
        INSERT INTO notification (customer_id, type, ref_id, title, content, is_read, occurred_at)
        VALUES (:customerId, :type, :refId, :title, :content, FALSE, :occurredAt)
        ON DUPLICATE KEY UPDATE notification_id = notification_id
        """,
            nativeQuery = true)
    void insertIfAbsent(
            @Param("customerId") Long customerId,
            @Param("type") String type,
            @Param("refId") Long refId,
            @Param("title") String title,
            @Param("content") String content,
            @Param("occurredAt") LocalDateTime occurredAt);
}
