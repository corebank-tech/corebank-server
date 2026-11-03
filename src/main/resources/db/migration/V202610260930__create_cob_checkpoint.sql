-- ====================================================================
-- V202610260930__create_cob_checkpoint.sql
-- COB 재시작 체크포인트 (P5, #663)
--
-- CobRunnerService가 마감 대상 영업일(COB_DATE)과 스텝별 완료 여부를 남긴다.
-- 영업일 전환 뒤에도 어느 날짜를 이어서 마감할지 알 수 있게 한다.
-- batch_execution_history(관측용, append-only)와 달리 재시작 근거로 쓴다.
-- ====================================================================
CREATE TABLE cob_checkpoint
(
    cob_checkpoint_id BIGINT       NOT NULL AUTO_INCREMENT,
    cob_date          DATE         NOT NULL COMMENT '마감 대상 영업일(COB_DATE)',
    step_name         VARCHAR(100) NOT NULL COMMENT 'CobStep.name(). 영업일 전환은 business-date-advance',
    status            VARCHAR(10)  NOT NULL COMMENT 'PENDING / SUCCESS',
    updated_at        DATETIME(6)  NOT NULL COMMENT '마지막 상태 변경 시각. 애플리케이션이 KST로 채운다',
    PRIMARY KEY (cob_checkpoint_id),
    UNIQUE KEY uk_cob_checkpoint_date_step (cob_date, step_name)
) ENGINE=InnoDB COMMENT='COB 재시작 체크포인트 (#663)';
