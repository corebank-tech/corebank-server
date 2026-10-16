-- ====================================================================
-- V202610091902__add_terms_view_history_expires_index.sql
-- P4 (#580)
--
-- 만료 약관 열람 이력 정리 배치가 expires_at 범위만 훑고 잠그도록 인덱스를 추가한다.
-- 인덱스가 없으면 DELETE 가 테이블 전체를 훑으며 행을 잠가 열람 upsert 를 막는다.
-- ====================================================================
CREATE INDEX ix_terms_view_history_expires ON terms_view_history (expires_at);
