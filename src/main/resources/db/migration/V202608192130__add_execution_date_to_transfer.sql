-- ====================================================================
-- V202608192130__add_execution_date_to_transfer.sql
-- transfer.execution_date 추가 및 (source_type, source_id, execution_date) 유니크 제약 (#202)
--
-- SCHEDULED/AUTO 이체는 배치가 같은 회차(sourceId+실행일자)를 중복 실행할 수 있다.
-- 사전조회만으로는 동시 execute() 호출이 레이스컨디션으로 둘 다 통과할 수 있으므로,
-- DB 유니크 제약으로 같은 회차는 상태(PROCESSING/SUCCESS/ERROR)에 관계없이 단 1행만
-- 허용한다. IMMEDIATE는 source_type/source_id/execution_date가 모두 NULL이고,
-- MySQL은 NULL을 서로 다른 값으로 취급하므로 이 제약에 영향받지 않는다.
--
-- 주의: Flyway 적용 후에는 이 파일을 수정하지 마십시오 (체크섬 불변).
--       변경은 새 V 파일에 ALTER 로 작성합니다.
-- ====================================================================

ALTER TABLE transfer
    ADD COLUMN execution_date DATE NULL COMMENT 'sourceId와 함께 멱등키를 구성. SCHEDULED/AUTO 전용' AFTER source_id;

ALTER TABLE transfer
    DROP KEY ix_transfer_source;

ALTER TABLE transfer
    ADD UNIQUE KEY uk_transfer_source_execution_date (source_type, source_id, execution_date);
