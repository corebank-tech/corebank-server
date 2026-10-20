-- ====================================================================
-- V202610201221__insert_bank_code_common_code.sql
-- 공통코드 (P4, PH-33-② / #654)
--
-- 타행이체 은행 목록(GET /transfers/banks). code는 전문 기관코드 3자리다(fixed_length_message_spec.md §2).
-- 당행 088은 넣지 않는다 — 타행 전용 목록이고 전문 기관코드에도 담지 않는다.
-- 목록·이름은 FE 인계 문서(interbank_transfer_fe_handoff.md §3)와 같다.
-- ====================================================================

INSERT INTO common_code (code_group, code, code_name, sort_order, description, created_at, updated_at)
VALUES ('BANK_CODE', '004', 'KB국민은행', 1, '타행이체 수취 은행', NOW(6), NOW(6)),
       ('BANK_CODE', '011', 'NH농협은행', 2, '타행이체 수취 은행', NOW(6), NOW(6)),
       ('BANK_CODE', '020', '우리은행', 3, '타행이체 수취 은행', NOW(6), NOW(6)),
       ('BANK_CODE', '081', '하나은행', 4, '타행이체 수취 은행', NOW(6), NOW(6)),
       ('BANK_CODE', '090', '카카오뱅크', 5, '타행이체 수취 은행', NOW(6), NOW(6));
