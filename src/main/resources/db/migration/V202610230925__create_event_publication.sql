-- ====================================================================
-- V202610230925__create_event_publication.sql
-- 대상 테이블: EVENT_PUBLICATION (신규, 소유 P1)
--
-- 주의: Flyway 적용 후에는 이 파일을 수정하지 마십시오 (체크섬 불변).
--       변경은 새 V 파일에 ALTER 로 작성합니다.
--
-- Spring Modulith 이벤트 발행 기록 테이블(PH-96, #437). jar의 schemas/v2/schema-mysql.sql을 그대로 옮겼다.
-- 라이브러리가 대문자 이름으로 조회하므로 바꾸지 않고, 자동 생성은 끄고 Flyway가 관리한다.
-- 설계와 예외 사유는 docs/adr/0010-outbox-spring-modulith-event-publication-registry.md에 있다.
-- ====================================================================

CREATE TABLE IF NOT EXISTS EVENT_PUBLICATION
(
  ID                     VARCHAR(36) NOT NULL,
  LISTENER_ID            VARCHAR(512) NOT NULL,
  EVENT_TYPE             VARCHAR(512) NOT NULL,
  SERIALIZED_EVENT       VARCHAR(4000) NOT NULL,
  PUBLICATION_DATE       TIMESTAMP(6) NOT NULL,
  COMPLETION_DATE        TIMESTAMP(6) DEFAULT NULL NULL,
  STATUS                 VARCHAR(20),
  COMPLETION_ATTEMPTS    INT,
  LAST_RESUBMISSION_DATE TIMESTAMP(6) DEFAULT NULL NULL,
  PRIMARY KEY (ID),
  INDEX EVENT_PUBLICATION_BY_COMPLETION_DATE_IDX (COMPLETION_DATE)
);
