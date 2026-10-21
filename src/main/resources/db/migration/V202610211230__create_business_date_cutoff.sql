-- ====================================================================
-- V202610211230__create_business_date_cutoff.sql
-- 영업일 마감 cutoff (P5, #556)
--
-- COB가 영업일 D를 넘길 때 BUSINESS_DATE 전환과 같은 트랜잭션으로 CLOSED 행을 남긴다.
-- 이 행이 레플리카에 보이면 D 거래일 원장은 더 추가되지 않는다 (정보계 ETL 시작 신호).
-- ====================================================================
CREATE TABLE business_date_cutoff
(
    business_date_cutoff_id BIGINT      NOT NULL AUTO_INCREMENT,
    base_date               DATE        NOT NULL COMMENT '마감된 영업일(D)',
    status                  VARCHAR(10) NOT NULL COMMENT 'CLOSED',
    closed_at               DATETIME(6) NOT NULL COMMENT '마감 시각. 애플리케이션이 KST로 채운다',
    PRIMARY KEY (business_date_cutoff_id),
    UNIQUE KEY uk_business_date_cutoff_date_status (base_date, status)
) ENGINE=InnoDB COMMENT='영업일 마감 cutoff (#556)';
