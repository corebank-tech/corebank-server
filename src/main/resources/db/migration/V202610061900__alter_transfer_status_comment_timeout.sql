-- ====================================================================
-- V202610061900__alter_transfer_status_comment_timeout.sql
-- 이체 처리 불명(TIMEOUT) 상태 반영 (P4, PH-80 S2분 / #564)
--
-- 대외 전문 무응답으로 결과를 모르는 이체가 TIMEOUT으로 커밋된다. 값은 주석으로만 관리하므로 주석만 바꾼다.
-- auto_transfer_execution·product_subscription의 status는 TIMEOUT을 쓰지 않아 그대로 둔다(#554).
-- ====================================================================

-- 타입·NULL을 그대로 둬야 메타데이터만 바뀐다. rebuild가 필요해지면 조용히 돌지 말고 실패하도록 알고리즘을 못박는다
ALTER TABLE transfer
    MODIFY COLUMN status VARCHAR(12) NOT NULL COMMENT 'SUCCESS / ERROR / TIMEOUT (PROCESSING은 커밋되지 않음)',
    ALGORITHM = INPLACE, LOCK = NONE;
