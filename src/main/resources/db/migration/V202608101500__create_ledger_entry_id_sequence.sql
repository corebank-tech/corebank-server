-- ====================================================================
-- V202608101500__create_ledger_entry_id_sequence.sql
-- ledger_entry.ledger_entry_id 전용 채번 테이블 (P4)

-- ledger_entry는 파티션 키(occurred_at)를 포함한 복합 PK (ledger_entry_id, occurred_at)를 쓴다.
-- ledger_entry_id 컬럼 자체는 AUTO_INCREMENT이지만, Hibernate는 @IdClass 복합키를
-- 구성하는 필드에 IDENTITY 채번 전략을 지원하지 않는다
--   (org.hibernate.id.IdentifierGenerationException: Identity generation
--    isn't supported for composite ids — 통합 테스트로 실측 확인, docs/flyway_guide.md 4-3절 참고)
-- 그래서 ledger_entry 자체의 AUTO_INCREMENT는 애플리케이션에서 활용할 수 없다.
--
-- 대신 다른 컬럼이 없는 이 전용 테이블에 빈 행을 INSERT해 그 결과로 생성되는
-- AUTO_INCREMENT 값만 취해 ledger_entry_id로 사용한다(Hibernate TABLE 채번과 동일한 발상).
-- 애플리케이션 측 사용처: LedgerEntryIdGenerator.

-- 주의: Flyway 적용 후에는 이 파일을 수정하지 마십시오 (체크섬 불변).
--       변경은 새 V 파일에 ALTER 로 작성합니다.
-- ====================================================================

CREATE TABLE ledger_entry_id_sequence (
    sequence_id BIGINT NOT NULL AUTO_INCREMENT,
    PRIMARY KEY (sequence_id)
) ENGINE=InnoDB COMMENT='ledger_entry.ledger_entry_id 전용 채번 카운터. AUTO_INCREMENT 값만 취하기 위한 테이블로 다른 컬럼은 두지 않는다';
