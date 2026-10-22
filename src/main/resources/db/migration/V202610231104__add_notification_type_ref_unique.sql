-- ====================================================================
-- V202610231104__add_notification_type_ref_unique.sql
-- 대상 테이블: notification
--
-- 주의: Flyway 적용 후에는 이 파일을 수정하지 마십시오 (체크섬 불변).
--       변경은 새 V 파일에 ALTER 로 작성합니다.
--
-- 알림 구독자는 같은 이벤트를 두 번 받을 수 있다(At-Least-Once, ADR-0010). 업무 키(type, ref_id)가 같은
-- 알림은 DB가 한 건만 남기도록 막는다(PH-01, #706).
-- ====================================================================

ALTER TABLE notification
    ADD CONSTRAINT uk_noti_type_ref UNIQUE (type, ref_id);
