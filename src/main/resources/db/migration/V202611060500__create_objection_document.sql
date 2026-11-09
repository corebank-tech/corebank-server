-- ====================================================================
-- V202611060500__create_objection_document.sql
-- 대상 테이블: objection_document, objection_document_ocr (신규 · P3)
--
-- 이의제기 AI 심사 지원(D-36) PA-12, PA-20 서류 첨부. 파일 본문은 DB 에 두지 않고 저장소
-- (app.payment-stop.documents.root 아래 {objectionId}/{documentId})에 두며, DB 에는 키와 해시, 종류, 크기만 쓴다(D36).
-- 신분증 사본(ID_COPY)은 고유식별정보라 AES-GCM 으로 암호화해 저장하고, Agent 내부 API 의 목록, 내용, OCR 에서 빠진다(D37).
-- OCR 결과는 서버가 가명 처리한 텍스트만 저장한다. 증빙(EVIDENCE)만 OCR 행을 둔다.
-- ====================================================================

CREATE TABLE objection_document (
    document_id   BIGINT       NOT NULL AUTO_INCREMENT,
    objection_id  BIGINT       NOT NULL,
    kind          VARCHAR(10)  NOT NULL COMMENT 'EVIDENCE 증빙 / ID_COPY 신분증 사본',
    file_name     VARCHAR(255) NOT NULL COMMENT '올린 파일 이름. 경로는 떼고 저장한다',
    content_type  VARCHAR(30)  NOT NULL COMMENT '매직 바이트로 판정한 형식. application/pdf / image/png / image/jpeg',
    size_bytes    BIGINT       NOT NULL,
    sha256        CHAR(64)     NOT NULL COMMENT '원본 파일의 SHA-256(16진수)',
    storage_key   VARCHAR(100) NOT NULL COMMENT '저장소 안 상대 경로 {objectionId}/{documentId}',
    encrypted     BOOLEAN      NOT NULL COMMENT 'AES-GCM 암호화 저장 여부. ID_COPY 는 항상 true',
    round         TINYINT      NOT NULL COMMENT '0 접수, 1~2 보완 회차',
    uploaded_by   BIGINT       NOT NULL COMMENT '올린 사람의 customer_id',
    uploader_role VARCHAR(10)  NOT NULL COMMENT 'HOLDER 명의인 / ADMIN 관리자 대리 업로드',
    uploaded_at   DATETIME(6)  NOT NULL,

    PRIMARY KEY (document_id),
    KEY ix_objection_document_objection (objection_id, document_id),
    CONSTRAINT fk_objection_document_objection FOREIGN KEY (objection_id) REFERENCES objection (objection_id),
    CONSTRAINT ck_objection_document_kind CHECK (kind IN ('EVIDENCE', 'ID_COPY')),
    CONSTRAINT ck_objection_document_type CHECK (content_type IN ('application/pdf', 'image/png', 'image/jpeg')),
    CONSTRAINT ck_objection_document_size CHECK (size_bytes > 0 AND size_bytes <= 10485760),
    CONSTRAINT ck_objection_document_round CHECK (round BETWEEN 0 AND 2),
    CONSTRAINT ck_objection_document_id_copy CHECK (kind <> 'ID_COPY' OR encrypted = TRUE),
    CONSTRAINT ck_objection_document_uploader CHECK (uploader_role IN ('HOLDER', 'ADMIN'))
) ENGINE=InnoDB COMMENT='이의제기 첨부 서류 (D-36)';

CREATE TABLE objection_document_ocr (
    document_id BIGINT      NOT NULL,
    status      VARCHAR(10) NOT NULL COMMENT 'PENDING 대기 / DONE 완료 / FAILED 실패',
    pages       JSON        NULL COMMENT '페이지별 글자 영역. 텍스트는 서버가 가명 처리한 값',
    engine      VARCHAR(40) NULL COMMENT 'OCR 엔진 이름과 버전',
    updated_at  DATETIME(6) NOT NULL,

    PRIMARY KEY (document_id),
    CONSTRAINT fk_objection_document_ocr_document FOREIGN KEY (document_id) REFERENCES objection_document (document_id),
    CONSTRAINT ck_objection_document_ocr_status CHECK (status IN ('PENDING', 'DONE', 'FAILED')),
    CONSTRAINT ck_objection_document_ocr_pages CHECK (status <> 'DONE' OR pages IS NOT NULL)
) ENGINE=InnoDB COMMENT='이의제기 증빙 OCR 결과 (D-36)';
