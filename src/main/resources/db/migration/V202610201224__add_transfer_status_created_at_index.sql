-- ====================================================================
-- V202610201224__add_transfer_status_created_at_index.sql
-- P4 (PH-33-② / #654)
--
-- 조회거래 스케줄러가 1분마다 처리 불명(TIMEOUT) 타행 건을 찾는다. transfer는 운영 300만 행 규모라
-- 인덱스 없이는 매번 전체를 훑는다. TIMEOUT은 드물어 status 첫 칸만으로 범위가 작다.
-- ====================================================================

-- 인덱스 추가는 온라인으로 된다. 테이블 재작성이 필요해지면 조용히 돌지 말고 실패하도록 알고리즘을 못박는다
ALTER TABLE transfer
    ADD INDEX ix_transfer_status_created (status, created_at),
    ALGORITHM = INPLACE, LOCK = NONE;
