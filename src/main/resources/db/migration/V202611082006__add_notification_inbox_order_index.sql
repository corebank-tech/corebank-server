-- PH-04 #390: 알림 목록(최신순)을 인덱스 순서로 읽어 고객별 누적 알림 전체를 정렬하지 않게 한다
CREATE INDEX ix_noti_customer_occurred
    ON notification (customer_id, occurred_at DESC, notification_id DESC);
