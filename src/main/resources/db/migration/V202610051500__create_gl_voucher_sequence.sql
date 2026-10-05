-- ====================================================================
-- V202610051500__create_gl_voucher_sequence.sql
-- 전표번호 일련번호 채번 카운터 (P3, PH-21 / #452)

-- 주의: Flyway 적용 후에는 이 파일을 수정하지 마십시오 (체크섬 불변).
--       변경은 새 V 파일에 ALTER 로 작성합니다.
--
-- 전표번호 yyyyMMdd-TTT-NNNNNN 의 NNNNNN 을 (거래일, 유형)마다 1부터 센다
-- (docs/phase2/gl_journal_patterns.md §1). 구조는 transaction_sequence 와 같다.
-- 카운터는 기표 트랜잭션과 별도로 커밋하므로 기표가 롤백되면 결번이 생긴다.
--
-- V202609221558 머리말은 "전표 단위 차대변 일치를 DB 제약으로 건다(PH-21)" 고 적었지만
-- PH-21 에서 걸지 않기로 정했다. 근거는 gl_journal_patterns.md §2 다.
-- ====================================================================

CREATE TABLE gl_voucher_sequence (
    trade_date DATE        NOT NULL COMMENT '전표의 거래일',
    tx_type    VARCHAR(24) NOT NULL COMMENT 'gl_voucher.tx_type 과 같은 값',
    last_seq   INT         NOT NULL COMMENT '해당 거래일·유형의 마지막 일련번호',
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (trade_date, tx_type),
    CONSTRAINT ck_gl_voucher_sequence_range CHECK (last_seq BETWEEN 1 AND 999999)
) ENGINE=InnoDB COMMENT='전표번호 일련번호 채번 (PH-21)';
