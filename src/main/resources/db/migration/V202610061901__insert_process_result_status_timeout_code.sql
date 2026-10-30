-- ====================================================================
-- V202610061901__insert_process_result_status_timeout_code.sql
-- 공통코드 (P4, PH-80 S2분 / #564)
--
-- ProcessResultStatus.TIMEOUT의 화면 표시명을 등록한다. code는 서버 Enum 값과 문자열이 일치해야 한다
-- (schema_reference.md common_code 절, REQ-CMN-023). 표시명은 glossary 9번 "처리 불명"을 따른다.
--
-- ALTER(V202610061900)와 같은 파일에 두지 않는 이유: MySQL DDL은 암묵적 커밋이라, 한 파일에서
-- ALTER가 성공하고 INSERT가 실패하면 변경만 남은 채 이력이 실패로 기록된다.
-- ====================================================================

INSERT INTO common_code (code_group, code, code_name, sort_order, description, created_at, updated_at)
VALUES ('PROCESS_RESULT_STATUS', 'TIMEOUT', '처리 불명', 4, '대외 전문 무응답으로 결과 미확정. 조회거래로 확정', NOW(6), NOW(6));
