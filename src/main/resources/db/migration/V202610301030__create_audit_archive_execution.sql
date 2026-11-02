-- ====================================================================
-- V202610301030__create_audit_archive_execution.sql
-- 대상 테이블: audit_archive_execution (신규 · P1)
--
-- 감사 로그 WORM 적재의 날짜 · 경로별 완료 상태(PH-82, #788 리뷰). 다음 적재가 SUCCESS가 아닌 날만 복구한다.
-- SUCCESS인데 사본이 없으면 다시 올리지 않고 검증 배치가 불일치로 본다 — 지운 증거를 현재 DB로 덮지 않는다.
-- ====================================================================
CREATE TABLE audit_archive_execution
(
    audit_archive_execution_id BIGINT       NOT NULL AUTO_INCREMENT,
    archive_date               DATE         NOT NULL COMMENT '적재 대상 날짜(audit_log.requested_at 기준)',
    archive_type               VARCHAR(10)  NOT NULL COMMENT 'LOG / ACCESS',
    status                     VARCHAR(10)  NOT NULL COMMENT 'PENDING / SUCCESS / FAILURE',
    object_key                 VARCHAR(100) NOT NULL COMMENT '저장소 객체 키',
    row_count                  INT          NULL COMMENT '적재한 행 수. 성공 때만 채우고 0건도 남긴다',
    failure_reason             VARCHAR(500) NULL COMMENT '마지막 실패 사유',
    completed_at               DATETIME(6)  NULL COMMENT '성공 시각',
    updated_at                 DATETIME(6)  NOT NULL COMMENT '마지막 상태 변경 시각. 애플리케이션이 KST로 채운다',
    PRIMARY KEY (audit_archive_execution_id),
    UNIQUE KEY uk_audit_archive_execution_date_type (archive_date, archive_type)
) ENGINE=InnoDB COMMENT='감사 로그 WORM 적재 상태 (PH-82, #788)';
